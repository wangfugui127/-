package com.lsy.minimall.jobs;

import com.lsy.minimall.common.Values;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.order.OrderService;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {
  private final Db db;
  private final OrderService orders;
  private final Transactions tx;
  private final Events events;
  private final Logger log = LoggerFactory.getLogger(getClass());

  public BackgroundJobs(Db db, OrderService orders, Transactions tx, Events events) {
    this.db = db;
    this.orders = orders;
    this.tx = tx;
    this.events = events;
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 5000)
  public void closeExpired() {
    for (var row :
        db.rows(
            "SELECT id FROM mall_order WHERE status='PENDING_PAYMENT' AND"
                + " expires_at<=UTC_TIMESTAMP(3) ORDER BY expires_at,id LIMIT 100"))
      try {
        orders.closeExpired(Values.num(row.get("id")));
      } catch (Exception e) {
        log.error("Order timeout scan failed id={}", row.get("id"), e);
      }
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 5000)
  public void endActivities() {
    db.update(
        "UPDATE seckill_activity SET status='ENDED',version=version+1 WHERE status='PUBLISHED' AND"
            + " end_at<=UTC_TIMESTAMP(3)");
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 20000)
  public void repairRefunds() {
    for (var row :
        db.rows(
            "SELECT id,order_id FROM payment_record WHERE status='REFUND_PENDING' AND"
                + " updated_at<TIMESTAMPADD(SECOND,-30,UTC_TIMESTAMP(3)) LIMIT 100"))
      tx.run(
          () -> {
            db.require("SELECT id FROM mall_order WHERE id=? FOR UPDATE", row.get("orderId"));
            var p =
                db.require(
                    "SELECT status FROM payment_record WHERE id=? FOR UPDATE", row.get("id"));
            if (!"REFUND_PENDING".equals(p.get("status"))) return;
            var old =
                db.row(
                    "SELECT * FROM outbox_event WHERE event_type='MOCK_REFUND_REQUESTED' AND"
                        + " aggregate_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE",
                    row.get("id"));
            if (old == null)
              events.add(
                  "MOCK_REFUND_REQUESTED",
                  "PAYMENT",
                  Values.num(row.get("id")),
                  Map.of("paymentId", row.get("id")),
                  0);
            else if (Set.of("FAILED", "PUBLISHED").contains(Values.text(old.get("status"))))
              db.update(
                  "UPDATE outbox_event SET"
                      + " status='PENDING',next_attempt_at=UTC_TIMESTAMP(3),lease_until=NULL,last_error='Refund"
                      + " repair requested' WHERE id=?",
                  old.get("id"));
          });
  }

  @Scheduled(fixedDelay = 300000, initialDelay = 60000)
  public void auditStock() {
    var product =
        db.rows(
            "SELECT p.id,p.stock_available,COALESCE(SUM(l.delta),0) ledger_balance FROM product p"
                + " LEFT JOIN inventory_ledger l ON l.pool_type='PRODUCT' AND l.pool_id=p.id GROUP"
                + " BY p.id HAVING p.stock_available<>ledger_balance");
    var activity =
        db.rows(
            "SELECT a.id,a.stock_available,COALESCE(SUM(l.delta),0) ledger_balance FROM"
                + " seckill_activity a LEFT JOIN inventory_ledger l ON l.pool_type='ACTIVITY' AND"
                + " l.pool_id=a.id GROUP BY a.id HAVING a.stock_available<>ledger_balance");
    if (!product.isEmpty() || !activity.isEmpty())
      log.error("Inventory ledger mismatch product={},activity={}", product, activity);
    long failed = db.count("SELECT COUNT(*) FROM outbox_event WHERE status='FAILED'");
    if (failed > 0) log.error("Failed Outbox events: {}", failed);
  }
}
