package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("cart_item")
public class CartItem {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long userId;
  public Long productId;
  public Integer quantity;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
