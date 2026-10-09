package com.lsy.minimall.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.inventory.InventoryService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
  private final Db db;
  private final Transactions tx;
  private final InventoryService inventory;
  private final Events events;
  private final Operations ops;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final ProductMapper productMapper;
  private final Semaphore databaseReads = new Semaphore(8);

  public ProductService(
      Db db,
      Transactions tx,
      InventoryService inventory,
      Events events,
      Operations ops,
      StringRedisTemplate redis,
      ObjectMapper mapper,
      ProductMapper productMapper) {
    this.db = db;
    this.tx = tx;
    this.inventory = inventory;
    this.events = events;
    this.ops = ops;
    this.redis = redis;
    this.mapper = mapper;
    this.productMapper = productMapper;
  }

  public Map<String, Object> page(
      boolean admin, int page, int size, String category, String keyword, String status) {
    page = Math.max(1, page);
    size = Values.size(size);
    String where = admin ? " WHERE 1=1" : " WHERE status='ON_SALE'";
    List<Object> args = new ArrayList<>();
    if (category != null && !category.isBlank()) {
      where += " AND category_code=?";
      args.add(category);
    }
    if (keyword != null && !keyword.isBlank()) {
      if (keyword.length() > 50) throw BusinessException.bad("关键词过长");
      where += " AND name LIKE ?";
      args.add("%" + keyword + "%");
    }
    if (admin && status != null && !status.isBlank()) {
      where += " AND status=?";
      args.add(status);
    }
    long total = db.count("SELECT COUNT(*) FROM product" + where, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    return Map.of(
        "items",
        db.rows(
            "SELECT * FROM product" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray()),
        "page",
        page,
        "size",
        size,
        "total",
        total);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> detail(long id) {
    String key = "product:detail:" + id;
    String lock = "lock:product-load:" + id;
    String owner = Values.id();
    boolean acquired = false;
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        if (cached.equals("null")) throw BusinessException.missing();
        Map<String, Object> result = mapper.readValue(cached, Map.class);
        result.put("serverNow", Instant.now().toString());
        return result;
      }
      acquired =
          Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, owner, Duration.ofSeconds(3)));
      if (!acquired) throw new BusinessException("DEPENDENCY_UNAVAILABLE", "商品正在刷新，请稍后重试", 503);
      // Another instance may have filled the cache before this lock was acquired.
      cached = redis.opsForValue().get(key);
      if (cached != null) {
        redis.execute(
            new DefaultRedisScript<>(
                "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end"
                    + " return 0",
                Long.class),
            List.of(lock),
            owner);
        acquired = false;
        if (cached.equals("null")) throw BusinessException.missing();
        Map<String, Object> result = mapper.readValue(cached, Map.class);
        result.put("serverNow", Instant.now().toString());
        return result;
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      /* cache can fail open; transaction data still comes from DB */
    }
    boolean permitted = databaseReads.tryAcquire();
    try {
      if (!permitted) throw new BusinessException("DEPENDENCY_UNAVAILABLE", "商品查询繁忙，请稍后重试", 503);
      Product product = productMapper.selectById(id);
      if (product == null || !"ON_SALE".equals(product.status)) {
        try {
          redis.opsForValue().set(key, "null", Duration.ofSeconds(20));
        } catch (Exception ignored) {
        }
        throw BusinessException.missing();
      }
      var row = db.require("SELECT * FROM product WHERE id=?", id);
      row.put("serverNow", db.now().toString());
      try {
        redis
            .opsForValue()
            .set(key, Values.json(row), Duration.ofSeconds(60 + new Random().nextInt(61)));
      } catch (Exception ignored) {
      }
      return row;
    } finally {
      if (permitted) databaseReads.release();
      if (acquired)
        try {
          redis.execute(
              new DefaultRedisScript<>(
                  "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end"
                      + " return 0",
                  Long.class),
              List.of(lock),
              owner);
        } catch (Exception ignored) {
        }
    }
  }

  public Map<String, Object> create(Map<String, Object> b, long actor) {
    validate(b);
    int stock = Math.toIntExact(Values.num(b.getOrDefault("initialStock", 0)));
    if (stock < 0 || stock > 1_000_000) throw BusinessException.bad("初始库存超出范围");
    return tx.run(
        () -> {
          long id =
              db.insert(
                  "INSERT INTO"
                      + " product(name,category_code,specification,description,image_url,price_fen)"
                      + " VALUES (?,?,?,?,?,?)",
                  b.get("name"),
                  b.get("categoryCode"),
                  b.getOrDefault("specification", ""),
                  b.getOrDefault("description", ""),
                  b.getOrDefault("imageUrl", ""),
                  Values.num(b.get("priceFen")));
          if (stock > 0)
            inventory.change(
                "PRODUCT", id, id, stock, "PRODUCT_INIT:" + id, "INIT", null, null, actor);
          var result = db.require("SELECT * FROM product WHERE id=?", id);
          events.log("PRODUCT_CREATE", "PRODUCT", id, actor, null, result, null);
          return result;
        });
  }

  public Map<String, Object> update(long id, Map<String, Object> b, long actor) {
    validate(b);
    return tx.run(
        () -> {
          var before = db.require("SELECT * FROM product WHERE id=? FOR UPDATE", id);
          if (Values.num(before.get("version")) != Values.num(b.get("version")))
            throw BusinessException.conflict("VERSION_CONFLICT", "商品已被修改，请刷新");
          db.update(
              "UPDATE product SET"
                  + " name=?,category_code=?,specification=?,description=?,image_url=?,price_fen=?,version=version+1"
                  + " WHERE id=?",
              b.get("name"),
              b.get("categoryCode"),
              b.getOrDefault("specification", ""),
              b.getOrDefault("description", ""),
              b.getOrDefault("imageUrl", ""),
              Values.num(b.get("priceFen")),
              id);
          events.invalidate(id);
          var result = db.require("SELECT * FROM product WHERE id=?", id);
          events.log("PRODUCT_EDIT", "PRODUCT", id, actor, before, result, null);
          return result;
        });
  }

  public Map<String, Object> status(long id, String action, long version, long actor) {
    if (!Set.of("ON_SALE", "OFF_SALE").contains(action)) throw BusinessException.bad("商品状态动作无效");
    return tx.run(
        () -> {
          var row = db.require("SELECT * FROM product WHERE id=? FOR UPDATE", id);
          if (Values.num(row.get("version")) != version)
            throw BusinessException.conflict("VERSION_CONFLICT", "商品已被修改");
          db.update("UPDATE product SET status=?,version=version+1 WHERE id=?", action, id);
          events.invalidate(id);
          events.log("PRODUCT_STATUS", "PRODUCT", id, actor, row, Map.of("status", action), null);
          return db.require("SELECT * FROM product WHERE id=?", id);
        });
  }

  public Map<String, Object> adjust(long id, Map<String, Object> b, String key, long actor) {
    int delta = Math.toIntExact(Values.num(b.get("delta")));
    String reason = Values.required(b.get("reason"), 128);
    if (delta == 0 || Math.abs((long) delta) > 1_000_000)
      throw BusinessException.bad("库存变化需非0且不超100万");
    return ops.execute(
        actor,
        "STOCK_ADJUST",
        "PRODUCT",
        id,
        key,
        b,
        () -> {
          db.require("SELECT id FROM product WHERE id=? FOR UPDATE", id);
          inventory.change(
              "PRODUCT",
              id,
              id,
              delta,
              "ADJUST:" + actor + ":" + key,
              "ADMIN_ADJUST",
              null,
              null,
              actor);
          return db.require("SELECT * FROM product WHERE id=?", id);
        });
  }

  private void validate(Map<String, Object> b) {
    Values.required(b.get("name"), 100);
    if (!Set.of("BOOK", "DIGITAL", "LIFE", "OTHER").contains(Values.text(b.get("categoryCode"))))
      throw BusinessException.bad("分类不正确");
    Values.money(Values.num(b.get("priceFen")), 1);
    if (Values.text(b.get("specification")).length() > 128
        || Values.text(b.get("description")).length() > 5000
        || Values.text(b.get("imageUrl")).length() > 512) throw BusinessException.bad("商品字段过长");
    String url = Values.text(b.get("imageUrl"));
    if (!url.isEmpty() && !url.startsWith("https://") && !url.startsWith("/"))
      throw BusinessException.bad("图片地址需HTTPS或站内路径");
  }
}
