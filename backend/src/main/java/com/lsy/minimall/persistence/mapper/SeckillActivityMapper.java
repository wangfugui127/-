package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.SeckillActivity;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {
  @Update(
      "UPDATE seckill_activity SET status='ENDED',version=version+1 WHERE status='PUBLISHED' AND"
          + " end_at<=UTC_TIMESTAMP(3)")
  int endExpiredActivities();

  @Select(
      "SELECT a.id,a.stock_available,COALESCE(SUM(l.delta),0) ledger_balance FROM seckill_activity"
          + " a LEFT JOIN inventory_ledger l ON l.pool_type='ACTIVITY' AND l.pool_id=a.id GROUP BY"
          + " a.id HAVING a.stock_available<>ledger_balance")
  List<Map<String, Object>> auditStockRows();

  default List<Map<String, Object>> auditStock() {
    return MapperRows.list(auditStockRows());
  }

  default Map<String, Object> findIdentityByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<SeckillActivity>().select("id").eq("id", id).last("FOR UPDATE")));
  }

  @Select(
      "SELECT a.*,p.name product_name,p.specification,p.image_url,p.price_fen original_price_fen"
          + " FROM seckill_activity a JOIN product p ON p.id=a.product_id WHERE a.id=#{id} AND"
          + " a.status<>'DRAFT'")
  List<Map<String, Object>> findPublicDetailRows(@Param("id") Object id);

  default Map<String, Object> findPublicDetail(Object id) {
    return MapperRows.first(findPublicDetailRows(id));
  }

  default long createActivity(
      Object productId,
      Object title,
      Object activityPriceFen,
      Object plannedStock,
      Object startAt,
      Object endAt) {
    SeckillActivity entity = new SeckillActivity();
    entity.productId = MapperRows.number(productId);
    entity.title = MapperRows.text(title);
    entity.activityPriceFen = MapperRows.number(activityPriceFen);
    entity.plannedStock = MapperRows.integer(plannedStock);
    entity.startAt = MapperRows.timestamp(startAt);
    entity.endAt = MapperRows.timestamp(endAt);
    insert(entity);
    return Objects.requireNonNull(entity.id);
  }

  default Map<String, Object> findById(Object id) {
    return MapperRows.first(selectMaps(new QueryWrapper<SeckillActivity>().eq("id", id)));
  }

  default Map<String, Object> findProductIdentityById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<SeckillActivity>().select("product_id").eq("id", id)));
  }

  default Map<String, Object> findByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<SeckillActivity>().eq("id", id).last("FOR UPDATE")));
  }

  @Update(
      "UPDATE seckill_activity SET"
          + " product_id=#{productId},title=#{title},activity_price_fen=#{activityPriceFen},planned_stock=#{plannedStock},start_at=#{startAt},end_at=#{endAt},version=version+1"
          + " WHERE id=#{id}")
  int editActivity(
      @Param("productId") Object productId,
      @Param("title") Object title,
      @Param("activityPriceFen") Object activityPriceFen,
      @Param("plannedStock") Object plannedStock,
      @Param("startAt") Object startAt,
      @Param("endAt") Object endAt,
      @Param("id") Object id);

  @Select(
      "SELECT COUNT(*) FROM seckill_activity WHERE product_id=#{productId} AND id<>#{excludedId}"
          + " AND status='PUBLISHED' AND start_at<#{endAt} AND end_at>#{startAt}")
  long countOverlapping(
      @Param("productId") Object productId,
      @Param("excludedId") Object excludedId,
      @Param("endAt") Object endAt,
      @Param("startAt") Object startAt);

  @Update(
      "UPDATE seckill_activity SET stock_total=#{stockTotal},status='PUBLISHED',version=version+1"
          + " WHERE id=#{id}")
  int publishActivity(@Param("stockTotal") Object stockTotal, @Param("id") Object id);

  @Update("UPDATE seckill_activity SET status='DISABLED',version=version+1 WHERE id=#{id}")
  int disableActivity(@Param("id") Object id);

  @Update("UPDATE seckill_activity SET status='CLOSED',version=version+1 WHERE id=#{id}")
  int closeActivity(@Param("id") Object id);

  @Update(
      "UPDATE seckill_activity SET stock_available=stock_available+#{delta},version=version+1 WHERE"
          + " id=#{id} AND CAST(stock_available AS SIGNED)+#{delta} BETWEEN 0 AND 2147483647")
  int changeStock(@Param("delta") int delta, @Param("id") Object id);

  default Map<String, Object> findStockById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<SeckillActivity>().select("stock_available").eq("id", id)));
  }

  @Select({
    "<script>",
    "SELECT a.*,p.name product_name,p.image_url,p.price_fen original_price_fen FROM"
        + " seckill_activity a JOIN product p ON p.id=a.product_id WHERE 1=1",
    "<if test='!admin'> AND a.status &lt;&gt; 'DRAFT'</if>",
    "<if test='status != null and status.trim() != &quot;&quot;'> AND a.status=#{status}</if>",
    "ORDER BY a.id DESC",
    "</script>"
  })
  IPage<Map<String, Object>> findPage(
      IPage<Map<String, Object>> page,
      @Param("admin") boolean admin,
      @Param("status") String status);
}
