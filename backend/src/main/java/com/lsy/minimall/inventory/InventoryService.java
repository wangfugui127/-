package com.lsy.minimall.inventory;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.mapper.*;
import com.lsy.minimall.product.ProductMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class InventoryService {
  private final InventoryLedgerMapper ledger;
  private final ProductMapper products;
  private final SeckillActivityMapper activityMapper;
  private final Events events;

  public InventoryService(
      InventoryLedgerMapper ledger,
      ProductMapper products,
      SeckillActivityMapper activityMapper,
      Events events) {
    this.ledger = ledger;
    this.products = products;
    this.activityMapper = activityMapper;
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
    boolean productPool = pool.equals("PRODUCT");
    int n =
        productPool
            ? products.changeStock(delta, poolId)
            : activityMapper.changeStock(delta, poolId);
    if (n != 1) throw BusinessException.conflict("STOCK_NOT_ENOUGH", "可用库存不足或超过上限");
    long balance =
        Values.num(
            MapperRows.require(
                    productPool
                        ? products.findStockById(poolId)
                        : activityMapper.findStockById(poolId))
                .get("stockAvailable"));
    ledger.appendLedger(
        key, pool, poolId, productId, orderId, activityId, delta, balance, reason, operator);
    if (pool.equals("PRODUCT")) events.invalidate(productId);
  }
}
