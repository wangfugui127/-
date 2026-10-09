package com.lsy.minimall.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class Operations {
  private final Db db;
  private final Transactions tx;
  private final Events events;
  private final ObjectMapper mapper;

  public Operations(Db db, Transactions tx, Events events, ObjectMapper mapper) {
    this.db = db;
    this.tx = tx;
    this.events = events;
    this.mapper = mapper;
  }

  public Map<String, Object> execute(
      long actor,
      String action,
      String target,
      long id,
      String key,
      Object body,
      Supplier<Map<String, Object>> work) {
    Values.requestKey(key);
    String hash = Values.hash(Values.json(Map.of("targetId", id, "body", body)));
    var old = existing(actor, action, key, hash);
    if (old != null) return old;
    try {
      return tx.run(
          () -> {
            var result = work.get();
            events.log(
                action,
                target,
                id,
                actor,
                null,
                Map.of("requestHash", hash, "result", result),
                key);
            return result;
          });
    } catch (DuplicateKeyException e) {
      old = existing(actor, action, key, hash);
      if (old != null) return old;
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> existing(long actor, String action, String key, String hash) {
    var row =
        db.row(
            "SELECT after_state FROM operation_log WHERE actor_id=? AND action=? AND request_key=?",
            actor,
            action,
            key);
    if (row == null) return null;
    try {
      var stored = mapper.readValue(Values.text(row.get("afterState")), Map.class);
      if (!hash.equals(stored.get("requestHash")))
        throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "相同请求键内容不同");
      return (Map<String, Object>) stored.get("result");
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
