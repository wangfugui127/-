package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("operation_log")
public class OperationLog {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String actorType;
  public Long actorId;
  public String action;
  public String targetType;
  public Long targetId;
  public String requestKey;
  public String beforeState;
  public String afterState;
  public String traceId;
  public java.sql.Timestamp createdAt;
}
