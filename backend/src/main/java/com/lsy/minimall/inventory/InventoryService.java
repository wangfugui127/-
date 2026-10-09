package com.lsy.minimall.inventory;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class InventoryService {
  private final Db db;
  private final Events events;

  public InventoryService(Db db, Events events) {
    this.db = db;
    this.events = events;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void change(
      String pool,
      long poolId,
      long productId,
      int delta,
      String key,
      String reason,
      Long orderId,
      Long activityId,
      Long operator) {
    if (delta == 0) return;
    String table = pool.equals("PRODUCT") ? "product" : "seckill_activity";
    int n =
        db.update(
            "UPDATE "
                + table
                + " SET stock_available=stock_available+?,version=version+1 WHERE id=? AND"
                + " CAST(stock_available AS SIGNED)+? BETWEEN 0 AND 2147483647",
            delta,
            poolId,
            delta);
    if (n != 1) throw BusinessException.conflict("STOCK_NOT_ENOUGH", "可用库存不足或超过上限");
    long balance =
        Values.num(
            db.require("SELECT stock_available FROM " + table + " WHERE id=?", poolId)
                .get("stockAvailable"));
    db.update(
        "INSERT INTO"
            + " inventory_ledger(effect_key,pool_type,pool_id,product_id,order_id,activity_id,delta,balance_after,reason,operator_id)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        key,
        pool,
        poolId,
        productId,
        orderId,
        activityId,
        delta,
        balance,
        reason,
        operator);
    if (pool.equals("PRODUCT")) events.invalidate(productId);
  }
}
