package com.lsy.minimall.infrastructure;

import com.lsy.minimall.common.Values;
import com.lsy.minimall.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class Events {
  private final ClockMapper clock;
  private final OperationLogMapper operationLogs;
  private final OutboxEventMapper outbox;

  public Events(ClockMapper clock, OperationLogMapper operationLogs, OutboxEventMapper outbox) {
    this.clock = clock;
    this.operationLogs = operationLogs;
    this.outbox = outbox;
  }

  public void add(String type, String aggregate, long id, Map<String, Object> data, int delay) {
    String topic =
        type.equals("PRODUCT_CACHE_INVALIDATE")
            ? "minimall-cache"
            : type.startsWith("MOCK_") ? "minimall-payment" : "minimall-order";
    String tag =
        switch (type) {
          case "PRODUCT_CACHE_INVALIDATE" -> "PRODUCT_INVALIDATE";
          case "MOCK_PAYMENT_REQUESTED" -> "REQUESTED";
          case "MOCK_REFUND_REQUESTED" -> "REFUND";
          case "ORDER_TIMEOUT_CHECK" -> "TIMEOUT";
          default -> type;
        };
    String eventId = Values.id();
    var payload =
        Map.of(
            "eventId",
            eventId,
            "eventType",
            type,
            "schemaVersion",
            1,
            "aggregateType",
            aggregate,
            "aggregateId",
            Long.toString(id),
            "occurredAt",
            clock.now().toString(),
            "data",
            data);
    outbox.appendEvent(eventId, type, aggregate, id, topic, tag, delay, Values.json(payload));
  }

  public void invalidate(long productId) {
    add(
        "PRODUCT_CACHE_INVALIDATE",
        "PRODUCT",
        productId,
        Map.of("productId", Long.toString(productId)),
        0);
  }

  public void log(
      String action, String target, long id, Long actor, Object before, Object after, String key) {
    operationLogs.appendLog(
        actor == null ? "SYSTEM" : "USER",
        actor,
        action,
        target,
        id,
        key,
        before == null ? null : Values.json(before),
        after == null ? null : Values.json(after),
        org.slf4j.MDC.get("traceId"));
  }
}
