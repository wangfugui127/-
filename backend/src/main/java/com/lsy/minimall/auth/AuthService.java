package com.lsy.minimall.auth;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.Db;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final Db db;
  private final PasswordEncoder encoder;
  private final StringRedisTemplate redis;

  public AuthService(Db db, PasswordEncoder encoder, StringRedisTemplate redis) {
    this.db = db;
    this.encoder = encoder;
    this.redis = redis;
  }

  public Map<String, Object> register(String username, String password) {
    validate(username, password);
    try {
      long id =
          db.insert(
              "INSERT INTO mall_user(username,password_hash) VALUES (?,?)",
              username.toLowerCase(Locale.ROOT),
              encoder.encode(password));
      return Map.of("id", Long.toString(id), "username", username.toLowerCase(Locale.ROOT));
    } catch (DuplicateKeyException e) {
      throw BusinessException.conflict("USERNAME_EXISTS", "用户名已存在");
    }
  }

  public Map<String, Object> login(String username, String password) {
    var row = db.row("SELECT * FROM mall_user WHERE username=?", username.toLowerCase(Locale.ROOT));
    if (row == null
        || !"ACTIVE".equals(row.get("status"))
        || !encoder.matches(password, Values.text(row.get("passwordHash"))))
      throw new BusinessException("UNAUTHENTICATED", "用户名或密码错误", 401);
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis
        .opsForValue()
        .set("auth:session:" + Values.hash(token), Values.text(row.get("id")), Duration.ofHours(2));
    return Map.of(
        "token",
        token,
        "tokenType",
        "Bearer",
        "expiresIn",
        7200,
        "user",
        me(Values.num(row.get("id"))));
  }

  public Map<String, Object> me(long id) {
    return db.require("SELECT id,username,role,status FROM mall_user WHERE id=?", id);
  }

  public void logout(String header) {
    redis.delete("auth:session:" + Values.hash(header.substring(7)));
  }

  public void bootstrap(String password) {
    if (password == null
        || password.isBlank()
        || db.count("SELECT COUNT(*) FROM mall_user WHERE username='admin'") > 0) return;
    validate("admin", password);
    db.update(
        "INSERT INTO mall_user(username,password_hash,role) VALUES ('admin',?,'ADMIN')",
        encoder.encode(password));
  }

  private void validate(String username, String password) {
    if (username == null
        || !username.matches("[A-Za-z0-9_]{4,32}")
        || password == null
        || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw BusinessException.bad("用户名需4到32位字母数字下划线，密码需8位以上且不超过72字节");
  }
}
