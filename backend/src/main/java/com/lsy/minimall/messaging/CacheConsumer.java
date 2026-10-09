package com.lsy.minimall.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "minimall-cache",
    selectorExpression = "PRODUCT_INVALIDATE",
    consumerGroup = "minimall-product-cache",
    consumeThreadNumber = 4,
    consumeThreadMax = 4)
public class CacheConsumer implements RocketMQListener<String> {
  private final MessageService service;

  public CacheConsumer(MessageService service) {
    this.service = service;
  }

  public void onMessage(String message) {
    service.consume("minimall-product-cache", message);
  }
}
