package com.lsy.minimall.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("mall_order")
public class MallOrder {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String orderNo;
  public Long userId;
  public String orderType;
  public Long activityId;
  public String status;
  public Long totalFen;
  public Long payableFen;
  public String currency;
  public String requestKey;
  public String requestHash;
  public String receiverName;
  public String receiverPhone;
  public String receiverAddress;
  public java.sql.Timestamp expiresAt;
  public java.sql.Timestamp paidAt;
  public java.sql.Timestamp cancelledAt;
  public String cancelReason;
  public java.sql.Timestamp shippedAt;
  public java.sql.Timestamp completedAt;
  public String logisticsCompany;
  public String trackingNo;
  public Long version;
  public java.sql.Timestamp createdAt;
  public java.sql.Timestamp updatedAt;
}
