package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.OperationLog;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
  default int appendLog(
      Object actorType,
      Object actorId,
      Object action,
      Object targetType,
      Object targetId,
      Object requestKey,
      Object beforeState,
      Object afterState,
      Object traceId) {
    OperationLog entity = new OperationLog();
    entity.actorType = MapperRows.text(actorType);
    entity.actorId = MapperRows.number(actorId);
    entity.action = MapperRows.text(action);
    entity.targetType = MapperRows.text(targetType);
    entity.targetId = MapperRows.number(targetId);
    entity.requestKey = MapperRows.text(requestKey);
    entity.beforeState = MapperRows.text(beforeState);
    entity.afterState = MapperRows.text(afterState);
    entity.traceId = MapperRows.text(traceId);
    return insert(entity);
  }

  default Map<String, Object> findIdempotentResult(
      Object actorId, Object action, Object requestKey) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<OperationLog>()
                .select("after_state")
                .eq("actor_id", actorId)
                .eq("action", action)
                .eq("request_key", requestKey)));
  }

  default List<Map<String, Object>> findOrderLogs(Object orderId) {
    return MapperRows.list(
        selectMaps(
            new QueryWrapper<OperationLog>()
                .select("action", "actor_type", "actor_id", "created_at")
                .eq("target_type", "ORDER")
                .eq("target_id", orderId)
                .orderByAsc("id")));
  }
}
