package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("order_creation_lock")
public class OrderCreationLock {
  @TableId(value = "user_id", type = IdType.INPUT)
  public Long userId;
}
