package com.lsy.minimall.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.order.OrderService;
import com.lsy.minimall.payment.PaymentService;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageService {
  private final Db db;
  private final Transactions tx;
  private final OrderService orders;
  private final PaymentService payments;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public MessageService(
      Db db,
      Transactions tx,
      OrderService orders,
      PaymentService payments,
      StringRedisTemplate redis,
      ObjectMapper mapper) {
    this.db = db;
    this.tx = tx;
    this.orders = orders;
    this.payments = payments;
    this.redis = redis;
    this.mapper = mapper;
  }

  @SuppressWarnings("unchecked")
  public void consume(String group, String body) {
    Map<String, Object> event;
    try {
      event = mapper.readValue(body, Map.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid event", e);
    }
    String id = Values.required(event.get("eventId"), 64);
    String hash = Values.hash(body);
    if (Values.num(event.get("schemaVersion")) != 1)
      throw new IllegalArgumentException("Unsupported event schema");
    if (done(group, id, hash)) return;
    try {
      tx.run(
          () -> {
            db.update(
                "INSERT INTO consumer_event(consumer_name,event_id,payload_hash,result_code) VALUES"
                    + " (?,?,?,'PROCESSED')",
                group,
                id,
                hash);
            var data = (Map<String, Object>) event.get("data");
            switch (Values.text(event.get("eventType"))) {
              case "ORDER_TIMEOUT_CHECK" -> orders.closeExpired(Values.num(data.get("orderId")));
              case "PRODUCT_CACHE_INVALIDATE" ->
                  redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                      "redis.call('INCR',KEYS[1]); return redis.call('DEL',KEYS[2])", Long.class),
                      List.of("product:generation:" + data.get("productId"), "product:detail:" + data.get("productId")));
              case "MOCK_PAYMENT_REQUESTED" ->
                  payments.simulate(Values.num(data.get("paymentId")), id);
              case "MOCK_REFUND_REQUESTED" -> payments.refund(Values.num(data.get("paymentId")));
              default -> throw new IllegalArgumentException("Unsupported event type");
            }
          });
    } catch (DuplicateKeyException e) {
      if (!done(group, id, hash)) throw e;
    }
  }

  private boolean done(String group, String id, String hash) {
    var row =
        db.row(
            "SELECT payload_hash FROM consumer_event WHERE consumer_name=? AND event_id=?",
            group,
            id);
    if (row == null) return false;
    if (!hash.equals(row.get("payloadHash")))
      throw new IllegalArgumentException("Event ID payload conflict");
    return true;
  }
}
