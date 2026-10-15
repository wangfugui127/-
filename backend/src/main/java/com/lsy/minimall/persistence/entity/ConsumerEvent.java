package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("consumer_event")
public class ConsumerEvent {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String consumerName;
  public String eventId;
  public String payloadHash;
  public String resultCode;
  public java.sql.Timestamp processedAt;
}
