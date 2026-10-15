package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.OrderCreationLock;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderCreationLockMapper extends BaseMapper<OrderCreationLock> {
  @Insert(
      "INSERT INTO order_creation_lock(user_id) VALUES (#{userId}) ON DUPLICATE KEY UPDATE"
          + " user_id=VALUES(user_id)")
  int ensureGate(@Param("userId") Object userId);

  default Map<String, Object> lockGate(Object userId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<OrderCreationLock>()
                .select("user_id")
                .eq("user_id", userId)
                .last("FOR UPDATE")));
  }
}
