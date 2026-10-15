package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.OutboxEvent;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
  default int appendEvent(
      Object eventId,
      Object eventType,
      Object aggregateType,
      Object aggregateId,
      Object topic,
      Object tag,
      Object delayLevel,
      Object payload) {
    OutboxEvent entity = new OutboxEvent();
    entity.eventId = MapperRows.text(eventId);
    entity.eventType = MapperRows.text(eventType);
    entity.aggregateType = MapperRows.text(aggregateType);
    entity.aggregateId = MapperRows.number(aggregateId);
    entity.topic = MapperRows.text(topic);
    entity.tag = MapperRows.text(tag);
    entity.delayLevel = MapperRows.integer(delayLevel);
    entity.payload = MapperRows.text(payload);
    return insert(entity);
  }

  default Map<String, Object> findLatestRefundEventForUpdate(Object paymentId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<OutboxEvent>()
                .eq("event_type", "MOCK_REFUND_REQUESTED")
                .eq("aggregate_id", paymentId)
                .orderByDesc("id")
                .last("LIMIT 1 FOR UPDATE")));
  }

  @Update(
      "UPDATE outbox_event SET"
          + " status='PENDING',next_attempt_at=UTC_TIMESTAMP(3),lease_until=NULL,last_error='Refund"
          + " repair requested' WHERE id=#{id}")
  int retryRefundEvent(@Param("id") Object id);

  default long countFailed() {
    return selectCount(new QueryWrapper<OutboxEvent>().eq("status", "FAILED"));
  }

  @Select(
      "SELECT * FROM outbox_event WHERE (status='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(3)) OR"
          + " (status='SENDING' AND lease_until<UTC_TIMESTAMP(3)) ORDER BY id LIMIT 10 FOR UPDATE"
          + " SKIP LOCKED")
  List<Map<String, Object>> claimBatchRows();

  default List<Map<String, Object>> claimBatch() {
    return MapperRows.list(claimBatchRows());
  }

  @Update(
      "UPDATE outbox_event SET"
          + " status='SENDING',lease_owner=#{owner},lease_until=TIMESTAMPADD(SECOND,120,UTC_TIMESTAMP(3)),attempts=attempts+1"
          + " WHERE id=#{id}")
  int markSending(@Param("owner") Object owner, @Param("id") Object id);

  @Update(
      "UPDATE outbox_event SET status='PUBLISHED',published_at=UTC_TIMESTAMP(3),lease_until=NULL"
          + " WHERE id=#{id} AND lease_owner=#{owner} AND status='SENDING'")
  int markPublished(@Param("id") Object id, @Param("owner") Object owner);

  @Update(
      "UPDATE outbox_event SET"
          + " status=#{status},next_attempt_at=TIMESTAMPADD(SECOND,#{delaySeconds},UTC_TIMESTAMP(3)),lease_until=NULL,last_error=#{error}"
          + " WHERE id=#{id} AND lease_owner=#{owner} AND status='SENDING'")
  int markRetry(
      @Param("status") Object status,
      @Param("delaySeconds") Object delaySeconds,
      @Param("error") Object error,
      @Param("id") Object id,
      @Param("owner") Object owner);
}
