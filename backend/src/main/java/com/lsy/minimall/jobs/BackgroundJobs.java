package com.lsy.minimall.jobs;

import com.lsy.minimall.common.Values;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.order.OrderService;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.mapper.*;
import com.lsy.minimall.product.ProductMapper;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {
  private final MallOrderMapper orderMapper;
  private final OutboxEventMapper outbox;
  private final PaymentRecordMapper paymentRecords;
  private final ProductMapper products;
  private final SeckillActivityMapper activityMapper;
  private final OrderService orders;
  private final Transactions tx;
  private final Events events;
  private final Logger log = LoggerFactory.getLogger(getClass());

  public BackgroundJobs(
      MallOrderMapper orderMapper,
      OutboxEventMapper outbox,
      PaymentRecordMapper paymentRecords,
      ProductMapper products,
      SeckillActivityMapper activityMapper,
      OrderService orders,
      Transactions tx,
      Events events) {
    this.orderMapper = orderMapper;
    this.outbox = outbox;
    this.paymentRecords = paymentRecords;
    this.products = products;
    this.activityMapper = activityMapper;
    this.orders = orders;
    this.tx = tx;
    this.events = events;
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 5000)
  public void closeExpired() {
    for (var row : orderMapper.findExpiredPendingOrders())
      try {
        orders.closeExpired(Values.num(row.get("id")));
      } catch (Exception e) {
        log.error("Order timeout scan failed id={}", row.get("id"), e);
      }
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 5000)
  public void endActivities() {
    activityMapper.endExpiredActivities();
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 20000)
  public void repairRefunds() {
    for (var row : paymentRecords.findRefundsToRepair())
      tx.run(
          () -> {
            MapperRows.require(orderMapper.findIdentityByIdForUpdate(row.get("orderId")));
            var p = MapperRows.require(paymentRecords.findStatusByIdForUpdate(row.get("id")));
            if (!"REFUND_PENDING".equals(p.get("status"))) return;
            var old = outbox.findLatestRefundEventForUpdate(row.get("id"));
            if (old == null)
              events.add(
                  "MOCK_REFUND_REQUESTED",
                  "PAYMENT",
                  Values.num(row.get("id")),
                  Map.of("paymentId", row.get("id")),
                  0);
            else if (Set.of("FAILED", "PUBLISHED").contains(Values.text(old.get("status"))))
              outbox.retryRefundEvent(old.get("id"));
          });
  }

  @Scheduled(fixedDelay = 300000, initialDelay = 60000)
  public void auditStock() {
    var product = products.auditStock();
    var activity = activityMapper.auditStock();
    if (!product.isEmpty() || !activity.isEmpty())
      log.error("Inventory ledger mismatch product={},activity={}", product, activity);
    long failed = outbox.countFailed();
    if (failed > 0) log.error("Failed Outbox events: {}", failed);
  }
}
