package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("mall_user")
public class MallUser {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String username;
  public String passwordHash;
  public String role;
  public String status;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
