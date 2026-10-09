package com.lsy.minimall.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "minimall-order",
    selectorExpression = "TIMEOUT",
    consumerGroup = "minimall-order-timeout",
    consumeThreadNumber = 4,
    consumeThreadMax = 4)
public class TimeoutConsumer implements RocketMQListener<String> {
  private final MessageService service;

  public TimeoutConsumer(MessageService service) {
    this.service = service;
  }

  public void onMessage(String message) {
    service.consume("minimall-order-timeout", message);
  }
}
