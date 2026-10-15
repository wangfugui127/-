package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("seckill_purchase")
public class SeckillPurchase {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long activityId;
  public Long userId;
  public Long orderId;
  public java.sql.Timestamp createdAt;
}
