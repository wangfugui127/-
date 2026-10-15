package com.lsy.minimall.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.entity.ConsumerEvent;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConsumerEventMapper extends BaseMapper<ConsumerEvent> {
  default int recordProcessed(Object consumerName, Object eventId, Object payloadHash) {
    ConsumerEvent entity = new ConsumerEvent();
    entity.consumerName = MapperRows.text(consumerName);
    entity.eventId = MapperRows.text(eventId);
    entity.payloadHash = MapperRows.text(payloadHash);
    entity.resultCode = MapperRows.text("PROCESSED");
    return insert(entity);
  }

  default Map<String, Object> findPayloadHash(Object consumerName, Object eventId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<ConsumerEvent>()
                .select("payload_hash")
                .eq("consumer_name", consumerName)
                .eq("event_id", eventId)));
  }

  default Map<String, Object> findPaymentCallback(Object eventId) {
    return MapperRows.first(
        selectMaps(
            new QueryWrapper<ConsumerEvent>()
                .eq("consumer_name", "mock-payment-callback")
                .eq("event_id", eventId)));
  }

  default int recordPaymentCallback(Object eventId, Object payloadHash) {
    ConsumerEvent entity = new ConsumerEvent();
    entity.consumerName = MapperRows.text("mock-payment-callback");
    entity.eventId = MapperRows.text(eventId);
    entity.payloadHash = MapperRows.text(payloadHash);
    entity.resultCode = MapperRows.text("PROCESSED");
    return insert(entity);
  }
}
