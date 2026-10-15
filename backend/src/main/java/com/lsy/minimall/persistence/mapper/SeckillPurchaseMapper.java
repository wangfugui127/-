package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.SeckillPurchase;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SeckillPurchaseMapper extends BaseMapper<SeckillPurchase> {
  @Select(
      "SELECT COUNT(*) FROM seckill_purchase p JOIN mall_order o ON o.id=p.order_id"
          + " WHERE p.activity_id=#{activityId} AND p.user_id=#{userId}"
          + " AND o.status NOT IN ('CANCELLED','CLOSED')")
  long countByActivityAndUser(
      @Param("activityId") Object activityId, @Param("userId") Object userId);

  @Select(
      "SELECT p.order_id,o.status order_status FROM seckill_purchase p"
          + " JOIN mall_order o ON o.id=p.order_id"
          + " WHERE p.activity_id=#{activityId} AND p.user_id=#{userId}"
          + " AND o.status NOT IN ('CANCELLED','CLOSED')")
  List<Map<String, Object>> findActiveByActivityAndUserRows(
      @Param("activityId") Object activityId, @Param("userId") Object userId);

  default Map<String, Object> findActiveByActivityAndUser(Object activityId, Object userId) {
    return MapperRows.first(findActiveByActivityAndUserRows(activityId, userId));
  }

  default Map<String, Object> findByActivityAndUser(Object activityId, Object userId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<SeckillPurchase>()
                .eq("activity_id", activityId)
                .eq("user_id", userId)));
  }

  @Update(
      "UPDATE seckill_purchase SET order_id=#{orderId},created_at=UTC_TIMESTAMP(3)"
          + " WHERE activity_id=#{activityId} AND user_id=#{userId} AND order_id=#{previousOrderId}")
  int replaceReleasedPurchase(
      @Param("activityId") Object activityId,
      @Param("userId") Object userId,
      @Param("previousOrderId") Object previousOrderId,
      @Param("orderId") Object orderId);

  default int recordPurchase(Object activityId, Object userId, Object orderId) {
    SeckillPurchase entity = new SeckillPurchase();
    entity.activityId = MapperRows.number(activityId);
    entity.userId = MapperRows.number(userId);
    entity.orderId = MapperRows.number(orderId);
    return insert(entity);
  }
}
