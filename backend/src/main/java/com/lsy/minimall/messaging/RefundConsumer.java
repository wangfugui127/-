package com.lsy.minimall.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "minimall-payment",
    selectorExpression = "REFUND",
    consumerGroup = "minimall-mock-refund",
    consumeThreadNumber = 4,
    consumeThreadMax = 4)
public class RefundConsumer implements RocketMQListener<String> {
  private final MessageService service;

  public RefundConsumer(MessageService service) {
    this.service = service;
  }

  public void onMessage(String message) {
    service.consume("minimall-mock-refund", message);
  }
}
