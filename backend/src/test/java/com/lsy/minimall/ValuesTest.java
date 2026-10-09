package com.lsy.minimall;

import static org.junit.jupiter.api.Assertions.*;

import com.lsy.minimall.common.*;
import org.junit.jupiter.api.Test;

class ValuesTest {
  @Test
  void exactMoney() {
    assertEquals(1, Values.money(1, 1));
    assertEquals(197010, Values.money(1990, 99));
  }

  @Test
  void invalidQuantity() {
    assertThrows(BusinessException.class, () -> Values.money(100, 0));
    assertThrows(BusinessException.class, () -> Values.money(100, 100));
  }

  @Test
  void invalidPrice() {
    assertThrows(BusinessException.class, () -> Values.money(0, 1));
    assertThrows(BusinessException.class, () -> Values.money(Long.MAX_VALUE, 99));
  }

  @Test
  void requestKeyBounds() {
    assertThrows(BusinessException.class, () -> Values.requestKey("short"));
    assertThrows(BusinessException.class, () -> Values.requestKey("x".repeat(65)));
    assertEquals("a".repeat(32), Values.requestKey("a".repeat(32)));
  }

  @Test
  void stableCanonicalJson() {
    assertEquals(
        Values.json(java.util.Map.of("a", 1, "b", 2)),
        Values.json(java.util.Map.of("b", 2, "a", 1)));
  }

  @Test
  void stableHash() {
    assertEquals(64, Values.hash("订单").length());
    assertNotEquals(Values.hash("one"), Values.hash("two"));
  }
}
