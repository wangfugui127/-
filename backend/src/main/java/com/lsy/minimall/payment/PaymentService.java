package com.lsy.minimall.payment;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.order.OrderService;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.mapper.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final ClockMapper clock;
  private final ConsumerEventMapper consumerEvents;
  private final MallOrderMapper orderMapper;
  private final PaymentRecordMapper paymentRecords;
  private final Transactions tx;
  private final OrderService orders;
  private final Events events;
  private final String secret;

  public PaymentService(
      ClockMapper clock,
      ConsumerEventMapper consumerEvents,
      MallOrderMapper orderMapper,
      PaymentRecordMapper paymentRecords,
      Transactions tx,
      OrderService orders,
      Events events,
      @Value("${app.mock-payment.secret}") String secret) {
    this.clock = clock;
    this.consumerEvents = consumerEvents;
    this.orderMapper = orderMapper;
    this.paymentRecords = paymentRecords;
    this.tx = tx;
    this.orders = orders;
    this.events = events;
    this.secret = secret;
    if (secret.length() < 32)
      throw new IllegalArgumentException("Mock secret must be at least 32 characters");
  }

  public Map<String, Object> create(long orderId, long user, String scenario) {
    return create(orderId, user, scenario, null);
  }

  public Map<String, Object> create(long orderId, long user, String scenario, String requestKey) {
    if (requestKey != null) Values.requestKey(requestKey);
    if (!Set.of("SUCCESS", "FAILURE", "LATE_SUCCESS").contains(scenario))
      throw BusinessException.bad("模拟场景不正确");
    return tx.run(
        () -> {
          var order = MapperRows.require(orderMapper.findByIdAndUserForUpdate(orderId, user));
          var replay =
              requestKey == null ? null : paymentRecords.findByOrderAndRequest(orderId, requestKey);
          if (replay != null) {
            if (!scenario.equals(replay.get("mockScenario")))
              throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "相同支付请求键内容不同");
            replay.put("paymentId", replay.get("id"));
            return replay;
          }
          var old = paymentRecords.findLatestByOrder(orderId);
          if (old != null && !"FAILED".equals(old.get("status"))) {
            if (requestKey != null && "CREATED".equals(old.get("status")))
              throw BusinessException.conflict("PAYMENT_IN_PROGRESS", "已有支付正在处理，请等待当前结果");
            if (!scenario.equals(old.get("mockScenario")))
              throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "支付场景不能在重试时修改");
            old.put("paymentId", old.get("id"));
            return old;
          }
          if (!"PENDING_PAYMENT".equals(order.get("status")))
            throw BusinessException.conflict("ORDER_STATE_CONFLICT", "当前订单不可付款");
          if (!clock.now().isBefore(Instant.parse(Values.text(order.get("expiresAt")))))
            throw BusinessException.conflict("ORDER_EXPIRED", "订单已超过付款期限");
          long id =
              paymentRecords.createPayment(
                  Values.id(),
                  orderId,
                  order.get("payableFen"),
                  requestKey == null ? Values.id() : requestKey,
                  scenario);
          int delay = 0;
          if (scenario.equals("LATE_SUCCESS"))
            delay =
                Duration.between(clock.now(), Instant.parse(Values.text(order.get("expiresAt"))))
                            .toSeconds()
                        <= 60
                    ? 5
                    : 14;
          events.add(
              "MOCK_PAYMENT_REQUESTED",
              "PAYMENT",
              id,
              Map.of("paymentId", Long.toString(id)),
              delay);
          var result = MapperRows.require(paymentRecords.findById(id));
          result.put("paymentId", Long.toString(id));
          return result;
        });
  }

  public Map<String, Object> detail(long id, long user) {
    return MapperRows.require(paymentRecords.findOwnedPayment(id, user));
  }

  public String sign(String timestamp, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void verify(String timestamp, String signature, String body) {
    long stamp;
    try {
      stamp = Long.parseLong(timestamp);
    } catch (Exception e) {
      throw new BusinessException("INVALID_SIGNATURE", "回调时间戳无效", 401);
    }
    long now = Instant.now().getEpochSecond();
    if (stamp < now - 300
        || stamp > now + 300
        || signature == null
        || !MessageDigest.isEqual(
            sign(timestamp, body).getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)))
      throw new BusinessException("INVALID_SIGNATURE", "回调签名无效或过期", 401);
  }

  public Map<String, Object> callback(Map<String, Object> b) {
    String callbackId = Values.required(b.get("callbackId"), 64),
        no = Values.required(b.get("paymentNo"), 32),
        trade = Values.required(b.get("providerTradeNo"), 64),
        result = Values.required(b.get("result"), 16);
    if (!Set.of("SUCCESS", "FAILED").contains(result)) throw BusinessException.bad("支付结果无效");
    String hash = Values.hash(Values.json(b));
    var old = consumerEvents.findPaymentCallback(callbackId);
    if (old != null) {
      if (!hash.equals(old.get("payloadHash")))
        throw BusinessException.conflict("CALLBACK_CONFLICT", "回调标识内容冲突");
      return Map.of("processed", true, "duplicate", true);
    }
    try {
      return tx.run(() -> processCallback(b, callbackId, no, trade, result, hash));
    } catch (DuplicateKeyException e) {
      old = consumerEvents.findPaymentCallback(callbackId);
      if (old != null && hash.equals(old.get("payloadHash")))
        return Map.of("processed", true, "duplicate", true);
      throw e;
    }
  }

  private Map<String, Object> processCallback(
      Map<String, Object> b, String cb, String no, String trade, String result, String hash) {
    var locate = MapperRows.require(paymentRecords.findOrderIdentityByPaymentNo(no));
    long orderId = Values.num(locate.get("orderId"));
    var order = MapperRows.require(orderMapper.findByIdForUpdate(orderId));
    var pay = MapperRows.require(paymentRecords.findByPaymentNoForUpdate(no));
    if (Values.num(b.get("amountFen")) != Values.num(pay.get("amountFen"))
        || !"CNY".equals(b.get("currency"))
        || !trade.equals("MOCK:" + no))
      throw BusinessException.conflict("PAYMENT_MISMATCH", "支付金额、币种或交易号不匹配");
    boolean failure = "FAILURE".equals(pay.get("mockScenario"));
    if (failure && !result.equals("FAILED") || !failure && !result.equals("SUCCESS"))
      throw BusinessException.conflict("PAYMENT_MISMATCH", "回调与模拟渠道结果不符");
    consumerEvents.recordPaymentCallback(cb, hash);
    if (!"CREATED".equals(pay.get("status"))) return Map.of("processed", true, "duplicate", true);
    if (result.equals("FAILED")) {
      paymentRecords.markFailed(trade, pay.get("id"));
      return Map.of("processed", true);
    }
    boolean expired = !clock.now().isBefore(Instant.parse(Values.text(order.get("expiresAt"))));
    if ("PENDING_PAYMENT".equals(order.get("status")) && !expired) {
      orderMapper.markPaid(orderId);
      paymentRecords.markSucceeded(trade, pay.get("id"));
      events.log(
          "ORDER_PAY",
          "ORDER",
          orderId,
          null,
          Map.of("status", "PENDING_PAYMENT"),
          Map.of("status", "PAID"),
          null);
    } else {
      if ("PENDING_PAYMENT".equals(order.get("status")))
        orders.closeLocked(order, "CLOSED", "支付回调到达时已超期", null);
      paymentRecords.markRefundPending(trade, pay.get("id"));
      events.add(
          "MOCK_REFUND_REQUESTED",
          "PAYMENT",
          Values.num(pay.get("id")),
          Map.of("paymentId", pay.get("id")),
          0);
    }
    return Map.of("processed", true);
  }

  public void simulate(long id, String eventId) {
    var pay = MapperRows.require(paymentRecords.findById(id));
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("callbackId", eventId + ":pay");
    b.put("paymentNo", pay.get("paymentNo"));
    b.put("providerTradeNo", "MOCK:" + pay.get("paymentNo"));
    b.put("amountFen", pay.get("amountFen"));
    b.put("currency", "CNY");
    b.put("result", "FAILURE".equals(pay.get("mockScenario")) ? "FAILED" : "SUCCESS");
    b.put("occurredAt", pay.get("createdAt"));
    callback(b);
  }

  public void refund(long id) {
    tx.run(
        () -> {
          var locate = MapperRows.require(paymentRecords.findOrderIdentityById(id));
          MapperRows.require(orderMapper.findIdentityByIdForUpdate(locate.get("orderId")));
          var pay = MapperRows.require(paymentRecords.findByIdForUpdate(id));
          if (!"REFUND_PENDING".equals(pay.get("status"))) return;
          paymentRecords.markRefunded(id);
          events.log(
              "MOCK_REFUND",
              "PAYMENT",
              id,
              null,
              pay,
              Map.of("status", "REFUNDED", "refundNo", "REFUND:" + pay.get("paymentNo")),
              null);
        });
  }
}
