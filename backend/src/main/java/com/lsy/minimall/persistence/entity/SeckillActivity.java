package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("seckill_activity")
public class SeckillActivity {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long productId;
  public String title;
  public Long activityPriceFen;
  public Integer plannedStock;
  public Integer stockTotal;
  public Integer stockAvailable;
  public Integer perUserLimit;
  public java.sql.Timestamp startAt;
  public java.sql.Timestamp endAt;
  public String status;
  public Long version;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
