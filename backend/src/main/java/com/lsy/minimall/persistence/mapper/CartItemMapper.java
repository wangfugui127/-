package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.CartItem;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
// 说明继承的接口和对应操作的表
public interface CartItemMapper extends BaseMapper<CartItem> {
  @Select(
      "SELECT"
          + " c.id,c.product_id,c.quantity,p.name,p.image_url,p.specification,p.price_fen,p.stock_available,p.status,CASE"
          + " WHEN p.status='ON_SALE' AND p.stock_available>=c.quantity THEN 1 ELSE 0 END available"
          + " FROM cart_item c JOIN product p ON p.id=c.product_id WHERE c.user_id=#{userId} ORDER"
          + " BY c.id")
  List<Map<String, Object>> findCartViewByUserIdRows(@Param("userId") Object userId);

  default List<Map<String, Object>> findCartViewByUserId(Object userId) {
    return MapperRows.list(findCartViewByUserIdRows(userId));
  }

  default Map<String, Object> findByUserAndProduct(Object userId, Object productId) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<CartItem>().eq("user_id", userId).eq("product_id", productId)));
  }

  @Insert(
      "INSERT INTO cart_item(user_id,product_id,quantity) VALUES"
          + " (#{userId},#{productId},#{quantity}) ON DUPLICATE KEY UPDATE quantity=#{newQuantity}")
  int upsertQuantity(
      @Param("userId") Object userId,
      @Param("productId") Object productId,
      @Param("quantity") Object quantity,
      @Param("newQuantity") Object newQuantity);

  @Update("UPDATE cart_item SET quantity=#{quantity} WHERE id=#{id} AND user_id=#{userId}")
  int updateQuantity(
      @Param("quantity") Object quantity, @Param("id") Object id, @Param("userId") Object userId);

  default Map<String, Object> findByIdAndUser(Object id, Object userId) {
    return MapperRows.first(
        selectMaps(new QueryWrapper<CartItem>().eq("id", id).eq("user_id", userId)));
  }

  @Delete("DELETE FROM cart_item WHERE id=#{id} AND user_id=#{userId}")
  int deleteByIdAndUser(@Param("id") Object id, @Param("userId") Object userId);

  default Map<String, Object> findByIdAndUserForUpdate(Object id, Object userId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<CartItem>().eq("id", id).eq("user_id", userId).last("FOR UPDATE")));
  }
}
