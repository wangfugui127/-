package com.lsy.minimall.messaging;

import com.lsy.minimall.common.Values;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.persistence.mapper.*;
import java.util.*;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class OutboxPublisher {
  private final OutboxEventMapper outbox;
  private final Transactions tx;
  private final RocketMQTemplate mq;
  private final Logger log = LoggerFactory.getLogger(getClass());

  public OutboxPublisher(OutboxEventMapper outbox, Transactions tx, RocketMQTemplate mq) {
    this.outbox = outbox;
    this.tx = tx;
    this.mq = mq;
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 3000)
  public void publish() {
    String owner = Values.id();
    List<Map<String, Object>> batch;
    try {
      batch =
          tx.run(
              () -> {
                var rows = outbox.claimBatch();
                for (var row : rows) outbox.markSending(owner, row.get("id"));
                return rows;
              });
    } catch (Exception e) {
      log.warn("Outbox claim failed", e);
      return;
    }
    for (var event : batch)
      try {
        var message =
            MessageBuilder.withPayload(Values.text(event.get("payload")))
                .setHeader(RocketMQHeaders.KEYS, event.get("eventId"))
                .build();
        var result =
            mq.syncSend(
                event.get("topic") + ":" + event.get("tag"),
                message,
                3000,
                Math.toIntExact(Values.num(event.get("delayLevel"))));
        if (result.getSendStatus() != org.apache.rocketmq.client.producer.SendStatus.SEND_OK)
          throw new IllegalStateException("Broker did not acknowledge durable send");
        outbox.markPublished(event.get("id"), owner);
      } catch (Exception e) {
        int attempts = Math.toIntExact(Values.num(event.get("attempts"))) + 1;
        int delay = Math.min(60, 1 << Math.min(attempts, 6));
        String error = Values.text(e.getMessage());
        if (error.length() > 1000) error = error.substring(0, 1000);
        outbox.markRetry(
            attempts >= 20 ? "FAILED" : "PENDING", delay, error, event.get("id"), owner);
        log.warn("Event {} send failed; retry {}", event.get("eventId"), attempts);
      }
  }
}
