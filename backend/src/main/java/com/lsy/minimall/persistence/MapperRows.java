package com.lsy.minimall.persistence;

import com.lsy.minimall.common.BusinessException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

// MyBatis 执行 SQL → 把每行结果映射成 Map
// MapperRows 整理 Map：字段名改成驼峰、ID 转成字符串、时间转成文本
/** Conversion at the Mapper boundary; does not execute SQL or obtain connections. */
public final class MapperRows {
  private MapperRows() {}

  public static List<Map<String, Object>> list(List<Map<String, Object>> rows) {
    return rows.stream().map(MapperRows::normalize).toList();
  }

  public static Map<String, Object> first(List<Map<String, Object>> rows) {
    return rows.isEmpty() ? null : normalize(rows.getFirst());
  }

  public static Map<String, Object> require(Map<String, Object> row) {
    if (row == null) throw BusinessException.missing();
    return row;
  }

  private static Map<String, Object> normalize(Map<String, Object> src) {
    Map<String, Object> dst = new LinkedHashMap<>();
    src.forEach(
        (key, value) -> {
          StringBuilder name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toCharArray()) {
            if (c == '_') {
              upper = true;
              continue;
            }
            name.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
          }
          String n = name.toString();
          Object v = value;
          if (value instanceof Timestamp t) v = t.toInstant().toString();
          else if (value instanceof LocalDateTime t) v = t.toInstant(ZoneOffset.UTC).toString();
          if (value != null && (n.equals("id") || n.endsWith("Id"))) v = value.toString();
          dst.put(n, v);
        });
    return dst;
  }

  public static String text(Object value) {
    return value == null ? null : value.toString();
  }

  public static Long number(Object value) {
    return value == null ? null : Long.valueOf(value.toString());
  }

  public static Integer integer(Object value) {
    return value == null ? null : Math.toIntExact(number(value));
  }

  public static Timestamp timestamp(Object value) {
    if (value == null) return null;
    if (value instanceof Timestamp t) return t;
    if (value instanceof LocalDateTime t) return Timestamp.valueOf(t);
    return Timestamp.from(value instanceof Instant t ? t : Instant.parse(value.toString()));
  }
}
