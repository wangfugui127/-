package com.lsy.minimall.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class Values {
  private Values() {}

  public static long num(Object v) {
    if (v == null) throw BusinessException.bad("缺少数值参数");
    try {
      return Long.parseLong(v.toString());
    } catch (Exception e) {
      throw BusinessException.bad("数值参数不正确");
    }
  }

  public static String text(Object v) {
    return v == null ? "" : v.toString();
  }

  public static String required(Object v, int max) {
    String s = text(v).strip();
    if (s.isEmpty() || s.length() > max) throw BusinessException.bad("文本必填或超出长度");
    return s;
  }

  public static String id() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String json(Object obj) {
    try {
      return new ObjectMapper()
          .findAndRegisterModules()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot serialize", e);
    }
  }

  public static String requestKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}"))
      throw BusinessException.bad("Idempotency-Key需16到64位字母数字下划线或连字符");
    return key;
  }

  public static int size(int size) {
    return Math.max(1, Math.min(50, size));
  }

  public static long money(long price, int quantity) {
    try {
      if (price <= 0 || price > 100_000_000 || quantity < 1 || quantity > 99)
        throw BusinessException.bad("价格或数量超出范围");
      return Math.multiplyExact(price, (long) quantity);
    } catch (ArithmeticException e) {
      throw BusinessException.bad("金额超出范围");
    }
  }
}
