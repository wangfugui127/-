package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.PaymentRecord;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
  @Select(
      "SELECT id,order_id FROM payment_record WHERE status='REFUND_PENDING' AND"
          + " updated_at<TIMESTAMPADD(SECOND,-30,UTC_TIMESTAMP(3)) LIMIT 100")
  List<Map<String, Object>> findRefundsToRepairRows();

  default List<Map<String, Object>> findRefundsToRepair() {
    return MapperRows.list(findRefundsToRepairRows());
  }

  default Map<String, Object> findStatusByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>().select("status").eq("id", id).last("FOR UPDATE")));
  }

  default Map<String, Object> findLatestSummaryByOrder(Object orderId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>()
                .select(
                    "id",
                    "payment_no",
                    "status",
                    "amount_fen",
                    "paid_at",
                    "refunded_at",
                    "request_key")
                .eq("order_id", orderId)
                .orderByDesc("id")
                .last("LIMIT 1")));
  }

  default Map<String, Object> findByOrderAndRequest(Object orderId, Object requestKey) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>()
                .eq("order_id", orderId)
                .eq("request_key", requestKey)));
  }

  default Map<String, Object> findLatestByOrder(Object orderId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>()
                .eq("order_id", orderId)
                .orderByDesc("id")
                .last("LIMIT 1")));
  }

  default long createPayment(
      Object paymentNo, Object orderId, Object amountFen, Object requestKey, Object mockScenario) {
    PaymentRecord entity = new PaymentRecord();
    entity.paymentNo = MapperRows.text(paymentNo);
    entity.orderId = MapperRows.number(orderId);
    entity.amountFen = MapperRows.number(amountFen);
    entity.currency = MapperRows.text("CNY");
    entity.status = MapperRows.text("CREATED");
    entity.requestKey = MapperRows.text(requestKey);
    entity.mockScenario = MapperRows.text(mockScenario);
    insert(entity);
    return Objects.requireNonNull(entity.id);
  }

  default Map<String, Object> findById(Object id) {
    return MapperRows.first(selectMaps(new QueryWrapper<PaymentRecord>().eq("id", id)));
  }

  @Select(
      "SELECT p.* FROM payment_record p JOIN mall_order o ON o.id=p.order_id WHERE p.id=#{id} AND"
          + " o.user_id=#{userId}")
  List<Map<String, Object>> findOwnedPaymentRows(
      @Param("id") Object id, @Param("userId") Object userId);

  default Map<String, Object> findOwnedPayment(Object id, Object userId) {
    return MapperRows.first(findOwnedPaymentRows(id, userId));
  }

  default Map<String, Object> findOrderIdentityByPaymentNo(Object paymentNo) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>().select("order_id").eq("payment_no", paymentNo)));
  }

  default Map<String, Object> findByPaymentNoForUpdate(Object paymentNo) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<PaymentRecord>().eq("payment_no", paymentNo).last("FOR UPDATE")));
  }

  @Update(
      "UPDATE payment_record SET"
          + " status='FAILED',failure_reason='模拟支付失败',provider_trade_no=#{tradeNo},version=version+1"
          + " WHERE id=#{id}")
  int markFailed(@Param("tradeNo") Object tradeNo, @Param("id") Object id);

  @Update(
      "UPDATE payment_record SET"
          + " status='SUCCEEDED',provider_trade_no=#{tradeNo},paid_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id}")
  int markSucceeded(@Param("tradeNo") Object tradeNo, @Param("id") Object id);

  @Update(
      "UPDATE payment_record SET"
          + " status='REFUND_PENDING',provider_trade_no=#{tradeNo},paid_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id}")
  int markRefundPending(@Param("tradeNo") Object tradeNo, @Param("id") Object id);

  default Map<String, Object> findOrderIdentityById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<PaymentRecord>().select("order_id").eq("id", id)));
  }

  default Map<String, Object> findByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<PaymentRecord>().eq("id", id).last("FOR UPDATE")));
  }

  @Update(
      "UPDATE payment_record SET status='REFUNDED',refunded_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id} AND status='REFUND_PENDING'")
  int markRefunded(@Param("id") Object id);
}
