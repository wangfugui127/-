package com.lsy.minimall.infrastructure;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Component;

@Component
public class Db {
  public final JdbcTemplate jdbc;

  public Db(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().map(this::normalize).toList();
  }

  public Map<String, Object> row(String sql, Object... args) {
    var rs = rows(sql, args);
    return rs.isEmpty() ? null : rs.getFirst();
  }

  public Map<String, Object> require(String sql, Object... args) {
    var r = row(sql, args);
    if (r == null) throw com.lsy.minimall.common.BusinessException.missing();
    return r;
  }

  public long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  public long insert(String sql, Object... args) {
    KeyHolder holder = new GeneratedKeyHolder();
    jdbc.update(
        conn -> {
          PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        holder);
    return Objects.requireNonNull(holder.getKey()).longValue();
  }

  public Instant now() {
    return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)", LocalDateTime.class)
        .toInstant(ZoneOffset.UTC);
  }

  private Map<String, Object> normalize(Map<String, Object> src) {
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
}
