package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("inventory_ledger")
public class InventoryLedger {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String effectKey;
  public String poolType;
  public Long poolId;
  public Long productId;
  public Long orderId;
  public Long activityId;
  public Integer delta;
  public Integer balanceAfter;
  public String reason;
  public Long operatorId;
  public java.sql.Timestamp createdAt;
}
