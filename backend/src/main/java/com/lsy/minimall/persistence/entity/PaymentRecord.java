package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("payment_record")
public class PaymentRecord {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String paymentNo;
  public Long orderId;
  public Long amountFen;
  public String currency;
  public String status;
  public String requestKey;
  public String providerTradeNo;
  public String mockScenario;
  public java.sql.Timestamp paidAt;
  public java.sql.Timestamp refundedAt;
  public String failureReason;
  public Long version;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
