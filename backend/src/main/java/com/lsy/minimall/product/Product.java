package com.lsy.minimall.product;

import com.baomidou.mybatisplus.annotation.*;

@TableName("product")
public class Product {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String name, categoryCode, specification, description, imageUrl, status;
  public Long priceFen, version;
  public Integer stockAvailable;
  public java.time.LocalDateTime createdAt, updatedAt;
}
