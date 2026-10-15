package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.InventoryLedger;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryLedgerMapper extends BaseMapper<InventoryLedger> {
  default int appendLedger(
      Object effectKey,
      Object poolType,
      Object poolId,
      Object productId,
      Object orderId,
      Object activityId,
      Object delta,
      Object balanceAfter,
      Object reason,
      Object operatorId) {
    InventoryLedger entity = new InventoryLedger();
    entity.effectKey = MapperRows.text(effectKey);
    entity.poolType = MapperRows.text(poolType);
    entity.poolId = MapperRows.number(poolId);
    entity.productId = MapperRows.number(productId);
    entity.orderId = MapperRows.number(orderId);
    entity.activityId = MapperRows.number(activityId);
    entity.delta = MapperRows.integer(delta);
    entity.balanceAfter = MapperRows.integer(balanceAfter);
    entity.reason = MapperRows.text(reason);
    entity.operatorId = MapperRows.number(operatorId);
    return insert(entity);
  }
}
