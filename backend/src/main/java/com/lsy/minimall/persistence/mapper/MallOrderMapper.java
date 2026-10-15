package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.MallOrder;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MallOrderMapper extends BaseMapper<MallOrder> {
  @Select(
      "SELECT id FROM mall_order WHERE status='PENDING_PAYMENT' AND expires_at<=UTC_TIMESTAMP(3)"
          + " ORDER BY expires_at,id LIMIT 100")
  List<Map<String, Object>> findExpiredPendingOrdersRows();

  default List<Map<String, Object>> findExpiredPendingOrders() {
    return MapperRows.list(findExpiredPendingOrdersRows());
  }

  default Map<String, Object> findIdentityByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<MallOrder>().select("id").eq("id", id).last("FOR UPDATE")));
  }

  default Map<String, Object> findOrderNoById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<MallOrder>().select("order_no").eq("id", id)));
  }

  default long countPendingByUser(Object userId) {
    return selectCount(
        new QueryWrapper<MallOrder>().eq("user_id", userId).eq("status", "PENDING_PAYMENT"));
  }

  default Map<String, Object> findByUserAndRequest(Object userId, Object requestKey) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<MallOrder>().eq("user_id", userId).eq("request_key", requestKey)));
  }

  default long createOrder(
      Object orderNo,
      Object userId,
      Object orderType,
      Object activityId,
      Object totalFen,
      Object payableFen,
      Object requestKey,
      Object requestHash,
      Object receiverName,
      Object receiverPhone,
      Object receiverAddress,
      Object expiresAt) {
    MallOrder entity = new MallOrder();
    entity.orderNo = MapperRows.text(orderNo);
    entity.userId = MapperRows.number(userId);
    entity.orderType = MapperRows.text(orderType);
    entity.activityId = MapperRows.number(activityId);
    entity.status = MapperRows.text("PENDING_PAYMENT");
    entity.totalFen = MapperRows.number(totalFen);
    entity.payableFen = MapperRows.number(payableFen);
    entity.requestKey = MapperRows.text(requestKey);
    entity.requestHash = MapperRows.text(requestHash);
    entity.receiverName = MapperRows.text(receiverName);
    entity.receiverPhone = MapperRows.text(receiverPhone);
    entity.receiverAddress = MapperRows.text(receiverAddress);
    entity.expiresAt = MapperRows.timestamp(expiresAt);
    insert(entity);
    return Objects.requireNonNull(entity.id);
  }

  default Map<String, Object> findByIdAndUserForUpdate(Object id, Object userId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<MallOrder>().eq("id", id).eq("user_id", userId).last("FOR UPDATE")));
  }

  default Map<String, Object> findByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<MallOrder>().eq("id", id).last("FOR UPDATE")));
  }

  @Update(
      "UPDATE mall_order SET"
          + " status=#{status},cancel_reason=#{reason},cancelled_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id} AND status='PENDING_PAYMENT'")
  int closePending(
      @Param("status") Object status, @Param("reason") Object reason, @Param("id") Object id);

  @Update(
      "UPDATE mall_order SET"
          + " status='SHIPPED',logistics_company=#{company},tracking_no=#{trackingNo},shipped_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id} AND status='PAID'")
  int shipPaid(
      @Param("company") Object company,
      @Param("trackingNo") Object trackingNo,
      @Param("id") Object id);

  @Update(
      "UPDATE mall_order SET status='COMPLETED',completed_at=UTC_TIMESTAMP(3),version=version+1"
          + " WHERE id=#{id}")
  int completeOrder(@Param("id") Object id);

  @Update(
      "UPDATE mall_order SET status='PAID',paid_at=UTC_TIMESTAMP(3),version=version+1 WHERE"
          + " id=#{id} AND status='PENDING_PAYMENT'")
  int markPaid(@Param("id") Object id);

  default long countPendingByActivity(Object activityId) {
    return selectCount(
        new QueryWrapper<MallOrder>()
            .eq("activity_id", activityId)
            .eq("status", "PENDING_PAYMENT"));
  }

  default Map<String, Object> findOwnedOrder(long id, long user, boolean admin) {
    QueryWrapper<MallOrder> query = new QueryWrapper<MallOrder>().eq("id", id);
    if (!admin) query.eq("user_id", user);
    return MapperRows.first(selectMaps(query));
  }

  default IPage<Map<String, Object>> findPage(
      long user, boolean admin, int page, int size, String status, String orderNo, Long userId) {
    QueryWrapper<MallOrder> query =
        new QueryWrapper<MallOrder>()
            .select(
                "id",
                "order_no",
                "user_id",
                "status",
                "order_type",
                "payable_fen",
                "expires_at",
                "created_at");
    if (!admin) query.eq("user_id", user);
    if (status != null && !status.isBlank()) query.eq("status", status);
    if (admin && orderNo != null && !orderNo.isBlank()) query.eq("order_no", orderNo);
    if (admin && userId != null) query.eq("user_id", userId);
    query.orderByDesc("id");
    return selectMapsPage(new Page<>(page, size), query);
  }
}
