package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("outbox_event")
public class OutboxEvent {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String eventId;
  public String eventType;
  public String aggregateType;
  public Long aggregateId;
  public String topic;
  public String tag;
  public Integer delayLevel;
  public String payload;
  public String status;
  public Integer attempts;
  public java.sql.Timestamp nextAttemptAt;
  public String leaseOwner;
  public java.sql.Timestamp leaseUntil;
  public String lastError;
  public java.sql.Timestamp publishedAt;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
