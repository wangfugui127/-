package com.lsy.minimall.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsy.minimall.persistence.MapperRows;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
  default Map<String, Object> findByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<Product>().eq("id", id).last("FOR UPDATE")));
  }

  default long countAll() {
    return selectCount(new QueryWrapper<Product>());
  }

  @Select(
      "SELECT p.id,p.stock_available,COALESCE(SUM(l.delta),0) ledger_balance FROM product p LEFT"
          + " JOIN inventory_ledger l ON l.pool_type='PRODUCT' AND l.pool_id=p.id GROUP BY p.id"
          + " HAVING p.stock_available<>ledger_balance")
  List<Map<String, Object>> auditStockRows();

  default List<Map<String, Object>> auditStock() {
    return MapperRows.list(auditStockRows());
  }

  default Map<String, Object> findIdentityByIdForUpdate(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<Product>().select("id").eq("id", id).last("FOR UPDATE")));
  }

  @Select("SELECT product.*, UTC_TIMESTAMP(3) AS server_now FROM product WHERE id=#{id}")
  List<Map<String, Object>> findDetailByIdRows(@Param("id") Object id);

  default Map<String, Object> findDetailById(Object id) {
    return MapperRows.first(findDetailByIdRows(id));
  }

  default long createProduct(
      Object name,
      Object categoryCode,
      Object specification,
      Object description,
      Object imageUrl,
      Object priceFen) {
    Product entity = new Product();
    entity.name = MapperRows.text(name);
    entity.categoryCode = MapperRows.text(categoryCode);
    entity.specification = MapperRows.text(specification);
    entity.description = MapperRows.text(description);
    entity.imageUrl = MapperRows.text(imageUrl);
    entity.priceFen = MapperRows.number(priceFen);
    insert(entity);
    return Objects.requireNonNull(entity.id);
  }

  default Map<String, Object> findById(Object id) {
    return MapperRows.first(selectMaps(new QueryWrapper<Product>().eq("id", id)));
  }

  @Update(
      "UPDATE product SET"
          + " name=#{name},category_code=#{categoryCode},specification=#{specification},description=#{description},image_url=#{imageUrl},price_fen=#{priceFen},version=version+1"
          + " WHERE id=#{id}")
  int editProduct(
      @Param("name") Object name,
      @Param("categoryCode") Object categoryCode,
      @Param("specification") Object specification,
      @Param("description") Object description,
      @Param("imageUrl") Object imageUrl,
      @Param("priceFen") Object priceFen,
      @Param("id") Object id);

  @Update("UPDATE product SET status=#{status},version=version+1 WHERE id=#{id}")
  int setStatus(@Param("status") Object status, @Param("id") Object id);

  default Map<String, Object> findIdentityById(Object id) {
    return MapperRows.first(selectMaps(new QueryWrapper<Product>().select("id").eq("id", id)));
  }

  @Update(
      "UPDATE product SET stock_available=stock_available+#{delta},version=version+1 WHERE id=#{id}"
          + " AND CAST(stock_available AS SIGNED)+#{delta} BETWEEN 0 AND 2147483647")
  int changeStock(@Param("delta") int delta, @Param("id") Object id);

  default Map<String, Object> findStockById(Object id) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<Product>().select("stock_available").eq("id", id)));
  }

  default IPage<Map<String, Object>> findPage(
      boolean admin, int page, int size, String category, String keyword, String status) {
    QueryWrapper<Product> query = new QueryWrapper<>();
    if (!admin) query.eq("status", "ON_SALE");
    if (category != null && !category.isBlank()) query.eq("category_code", category);
    if (keyword != null && !keyword.isBlank()) query.like("name", keyword);
    if (admin && status != null && !status.isBlank()) query.eq("status", status);
    query.orderByDesc("id");
    return selectMapsPage(new Page<>(page, size), query);
  }
}
