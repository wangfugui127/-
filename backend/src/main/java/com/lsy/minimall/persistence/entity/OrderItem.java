package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("order_item")
public class OrderItem {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long orderId;
  public Long productId;
  public String productName;
  public String specification;
  public String imageUrl;
  public Long unitPriceFen;
  public Integer quantity;
  public Long subtotalFen;
  public String stockSource;
  public Long activityId;
  public java.sql.Timestamp createdAt;
}
