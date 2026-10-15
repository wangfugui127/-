package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.OrderItem;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
  default int appendItem(
      Object orderId,
      Object productId,
      Object productName,
      Object specification,
      Object imageUrl,
      Object unitPriceFen,
      Object quantity,
      Object subtotalFen,
      Object stockSource,
      Object activityId) {
    OrderItem entity = new OrderItem();
    entity.orderId = MapperRows.number(orderId);
    entity.productId = MapperRows.number(productId);
    entity.productName = MapperRows.text(productName);
    entity.specification = MapperRows.text(specification);
    entity.imageUrl = MapperRows.text(imageUrl);
    entity.unitPriceFen = MapperRows.number(unitPriceFen);
    entity.quantity = MapperRows.integer(quantity);
    entity.subtotalFen = MapperRows.number(subtotalFen);
    entity.stockSource = MapperRows.text(stockSource);
    entity.activityId = MapperRows.number(activityId);
    return insert(entity);
  }

  default List<Map<String, Object>> findByOrder(Object orderId) {
    return MapperRows.list(
        selectMaps(new QueryWrapper<OrderItem>().eq("order_id", orderId).orderByAsc("id")));
  }

  default List<Map<String, Object>> findItemSummaries(Object orderId) {
    return MapperRows.list(
        selectMaps(
            new QueryWrapper<OrderItem>()
                .select("product_name", "image_url", "quantity")
                .eq("order_id", orderId)));
  }

  default List<Map<String, Object>> findByOrderSortedByProduct(Object orderId) {
    return MapperRows.list(
        selectMaps(new QueryWrapper<OrderItem>().eq("order_id", orderId).orderByAsc("product_id")));
  }
}
