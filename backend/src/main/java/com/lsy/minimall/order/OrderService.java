package com.lsy.minimall.order;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.inventory.InventoryService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final Db db;
  private final Transactions tx;
  private final InventoryService stock;
  private final Events events;
  private final int timeout;

  public OrderService(
      Db db,
      Transactions tx,
      InventoryService stock,
      Events events,
      @Value("${app.order.timeout-minutes}") int timeout) {
    this.db = db;
    this.tx = tx;
    this.stock = stock;
    this.events = events;
    this.timeout = timeout;
  }

  public Map<String, Object> create(long user, String key, Map<String, Object> body) {
    Values.requestKey(key);
    Map<String, Object> canonical = canonical(body);
    String hash = Values.hash(Values.json(Map.of("type", "NORMAL", "body", canonical)));
    var prior = existing(user, key, hash);
    if (prior != null) return prior;
    try {
      return tx.run(() -> createNormal(user, key, hash, canonical));
    } catch (DuplicateKeyException e) {
      prior = existing(user, key, hash);
      if (prior != null) return prior;
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> canonical(Map<String, Object> b) {
    String source = Values.required(b.get("source"), 16);
    if (!Set.of("DIRECT", "CART").contains(source))
      throw BusinessException.bad("source需DIRECT或CART");
    Map<String, Object> result = new TreeMap<>();
    result.put("source", source);
    result.put("expectedTotalFen", Values.num(b.get("expectedTotalFen")));
    result.put("receiver", receiver(b));
    List<Map<String, Object>> items = new ArrayList<>();
    List<String> cart = new ArrayList<>();
    if (b.get("items") instanceof List<?> list)
      for (Object entry : list) {
        if (!(entry instanceof Map<?, ?> m)) throw BusinessException.bad("商品项格式错误");
        items.add(
            Map.of(
                "productId",
                Long.toString(Values.num(m.get("productId"))),
                "quantity",
                Math.toIntExact(Values.num(m.get("quantity")))));
      }
    if (b.get("cartItemIds") instanceof List<?> list)
      for (Object id : list) cart.add(Long.toString(Values.num(id)));
    items.sort(Comparator.comparingLong(m -> Values.num(m.get("productId"))));
    cart.sort(Comparator.comparingLong(Long::parseLong));
    if (source.equals("DIRECT") && (items.isEmpty() || !cart.isEmpty())
        || source.equals("CART") && (cart.isEmpty() || !items.isEmpty())
        || items.size() > 20
        || cart.size() > 20) throw BusinessException.bad("商品和购物车参数互斥且最多20项");
    if (cart.stream().distinct().count() != cart.size()
        || items.stream().map(m -> m.get("productId")).distinct().count() != items.size())
      throw BusinessException.bad("商品项不能重复");
    result.put("items", items);
    result.put("cartItemIds", cart);
    return result;
  }

  public Map<String, String> receiver(Map<String, Object> b) {
    if (!(b.get("receiver") instanceof Map<?, ?> m)) throw BusinessException.bad("需要收货信息");
    String name = Values.required(m.get("name"), 64),
        phone = Values.required(m.get("phone"), 32),
        address = Values.required(m.get("address"), 256);
    if (phone.length() < 8 || address.length() < 5) throw BusinessException.bad("收货电话或地址不完整");
    return Map.of("name", name, "phone", phone, "address", address);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> createNormal(
      long user, String key, String hash, Map<String, Object> b) {
    List<Map<String, Object>> input = (List<Map<String, Object>>) b.get("items");
    List<String> carts = (List<String>) b.get("cartItemIds");
    if ("CART".equals(b.get("source"))) {
      input = new ArrayList<>();
      for (String id : carts) {
        var c = db.require("SELECT * FROM cart_item WHERE id=? AND user_id=?", id, user);
        input.add(Map.of("productId", c.get("productId"), "quantity", c.get("quantity")));
      }
      input.sort(Comparator.comparingLong(m -> Values.num(m.get("productId"))));
      for (var item : input)
        db.require("SELECT id FROM product WHERE id=? FOR UPDATE", item.get("productId"));
      input = new ArrayList<>();
      for (String cart : carts) {
        var c =
            db.require("SELECT * FROM cart_item WHERE id=? AND user_id=? FOR UPDATE", cart, user);
        input.add(Map.of("productId", c.get("productId"), "quantity", c.get("quantity")));
      }
      input.sort(Comparator.comparingLong(m -> Values.num(m.get("productId"))));
    }
    if (db.count(
            "SELECT COUNT(*) FROM mall_order WHERE user_id=? AND status='PENDING_PAYMENT'", user)
        >= 20) throw BusinessException.conflict("PENDING_ORDER_LIMIT", "待付款订单过多，请先处理");
    List<Map<String, Object>> lines = new ArrayList<>();
    long total = 0;
    for (var item : input) {
      long id = Values.num(item.get("productId"));
      int quantity = Math.toIntExact(Values.num(item.get("quantity")));
      var p = db.require("SELECT * FROM product WHERE id=? FOR UPDATE", id);
      if (!"ON_SALE".equals(p.get("status")))
        throw BusinessException.conflict("PRODUCT_UNAVAILABLE", "商品已下架");
      long subtotal = Values.money(Values.num(p.get("priceFen")), quantity);
      total = Math.addExact(total, subtotal);
      if (Values.num(p.get("stockAvailable")) < quantity)
        throw BusinessException.conflict("STOCK_NOT_ENOUGH", "商品库存不足");
      Map<String, Object> line = new HashMap<>(p);
      line.put("quantity", quantity);
      line.put("subtotal", subtotal);
      lines.add(line);
    }
    if (total != Values.num(b.get("expectedTotalFen")))
      throw BusinessException.conflict("PRICE_CHANGED", "商品价格有变化，请刷新确认");
    var rec = (Map<String, String>) b.get("receiver");
    long id = insertOrder(user, key, hash, "NORMAL", null, total, rec);
    String no =
        Values.text(db.require("SELECT order_no FROM mall_order WHERE id=?", id).get("orderNo"));
    for (var p : lines) {
      long product = Values.num(p.get("id"));
      int qty = Math.toIntExact(Values.num(p.get("quantity")));
      insertItem(id, p, qty, Values.num(p.get("priceFen")), "PRODUCT", null);
      stock.change(
          "PRODUCT",
          product,
          product,
          -qty,
          "ORDER_RESERVE:" + no + ":" + product,
          "ORDER_RESERVE",
          id,
          null,
          user);
    }
    for (String cart : carts)
      db.update("DELETE FROM cart_item WHERE id=? AND user_id=?", cart, user);
    afterCreate(id, user);
    return detail(id, user, false);
  }

  public Map<String, Object> existing(long user, String key, String hash) {
    var row = db.row("SELECT * FROM mall_order WHERE user_id=? AND request_key=?", user, key);
    if (row == null) return null;
    if (!hash.equals(row.get("requestHash")))
      throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "相同请求键内容不同");
    return detail(Values.num(row.get("id")), user, false);
  }

  public long insertOrder(
      long user,
      String key,
      String hash,
      String type,
      Long activity,
      long total,
      Map<String, String> rec) {
    return db.insert(
        "INSERT INTO"
            + " mall_order(order_no,user_id,order_type,activity_id,status,total_fen,payable_fen,request_key,request_hash,receiver_name,receiver_phone,receiver_address,expires_at)"
            + " VALUES (?,?,?,?,'PENDING_PAYMENT',?,?,?,?,?,?,?,?)",
        Values.id(),
        user,
        type,
        activity,
        total,
        total,
        key,
        hash,
        rec.get("name"),
        rec.get("phone"),
        rec.get("address"),
        Timestamp.from(db.now().plusSeconds(timeout * 60L)));
  }

  public void insertItem(
      long id, Map<String, Object> p, int quantity, long price, String pool, Long activity) {
    db.update(
        "INSERT INTO"
            + " order_item(order_id,product_id,product_name,specification,image_url,unit_price_fen,quantity,subtotal_fen,stock_source,activity_id)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        id,
        Values.num(p.get("id")),
        p.get("name"),
        p.get("specification"),
        p.get("imageUrl"),
        price,
        quantity,
        Values.money(price, quantity),
        pool,
        activity);
  }

  public void afterCreate(long id, long user) {
    events.add(
        "ORDER_TIMEOUT_CHECK",
        "ORDER",
        id,
        Map.of("orderId", Long.toString(id)),
        timeout == 1 ? 5 : 14);
    events.log("ORDER_CREATE", "ORDER", id, user, null, Map.of("status", "PENDING_PAYMENT"), null);
  }

  public Map<String, Object> detail(long id, long user, boolean admin) {
    var order =
        db.require(
            "SELECT * FROM mall_order WHERE id=?" + (admin ? "" : " AND user_id=?"),
            admin ? new Object[] {id} : new Object[] {id, user});
    order.put("items", db.rows("SELECT * FROM order_item WHERE order_id=? ORDER BY id", id));
    order.put(
        "payment",
        db.row(
            "SELECT id,payment_no,status,amount_fen,paid_at,refunded_at FROM payment_record WHERE"
                + " order_id=?",
            id));
    order.put("serverNow", db.now().toString());
    String state = Values.text(order.get("status"));
    order.put(
        "allowedActions",
        switch (state) {
          case "PENDING_PAYMENT" -> List.of("PAY", "CANCEL");
          case "SHIPPED" -> List.of("CONFIRM");
          case "PAID" -> admin ? List.of("SHIP") : List.of();
          default -> List.of();
        });
    if (admin)
      order.put(
          "logs",
          db.rows(
              "SELECT action,actor_type,actor_id,created_at FROM operation_log WHERE"
                  + " target_type='ORDER' AND target_id=? ORDER BY id",
              id));
    return order;
  }

  public Map<String, Object> page(
      long user, boolean admin, int page, int size, String status, String orderNo, String userId) {
    page = Math.max(page, 1);
    size = Values.size(size);
    String where = admin ? " WHERE 1=1" : " WHERE user_id=?";
    List<Object> args = new ArrayList<>();
    if (!admin) args.add(user);
    if (status != null && !status.isBlank()) {
      where += " AND status=?";
      args.add(status);
    }
    if (admin && orderNo != null && !orderNo.isBlank()) {
      where += " AND order_no=?";
      args.add(orderNo);
    }
    if (admin && userId != null && !userId.isBlank()) {
      where += " AND user_id=?";
      args.add(Values.num(userId));
    }
    long total = db.count("SELECT COUNT(*) FROM mall_order" + where, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var orders =
        db.rows(
            "SELECT id,order_no,user_id,status,order_type,payable_fen,expires_at,created_at FROM"
                + " mall_order"
                + where
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
            args.toArray());
    for (var o : orders)
      o.put(
          "items",
          db.rows(
              "SELECT product_name,image_url,quantity FROM order_item WHERE order_id=?",
              o.get("id")));
    return Map.of("items", orders, "total", total, "page", page, "size", size);
  }

  public Map<String, Object> cancel(long id, long user, String reason) {
    return tx.run(
        () -> {
          var order =
              db.require("SELECT * FROM mall_order WHERE id=? AND user_id=? FOR UPDATE", id, user);
          String state = Values.text(order.get("status"));
          if (state.equals("CANCELLED") || state.equals("CLOSED")) return detail(id, user, false);
          if (!state.equals("PENDING_PAYMENT"))
            throw BusinessException.conflict("ORDER_STATE_CONFLICT", "当前订单无法取消");
          closeLocked(order, "CANCELLED", reason, user);
          return detail(id, user, false);
        });
  }

  public void closeExpired(long id) {
    tx.run(
        () -> {
          var order = db.row("SELECT * FROM mall_order WHERE id=? FOR UPDATE", id);
          if (order != null
              && "PENDING_PAYMENT".equals(order.get("status"))
              && !db.now().isBefore(Instant.parse(Values.text(order.get("expiresAt")))))
            closeLocked(order, "CLOSED", "支付超时", null);
        });
  }

  public void closeLocked(Map<String, Object> order, String status, String reason, Long user) {
    long id = Values.num(order.get("id"));
    if (!"PENDING_PAYMENT".equals(order.get("status"))) return;
    var lines = db.rows("SELECT * FROM order_item WHERE order_id=? ORDER BY product_id", id);
    for (var item : lines)
      db.require("SELECT id FROM product WHERE id=? FOR UPDATE", item.get("productId"));
    for (var item : lines)
      if (item.get("activityId") != null)
        db.require("SELECT id FROM seckill_activity WHERE id=? FOR UPDATE", item.get("activityId"));
    if (db.update(
            "UPDATE mall_order SET"
                + " status=?,cancel_reason=?,cancelled_at=UTC_TIMESTAMP(3),version=version+1 WHERE"
                + " id=? AND status='PENDING_PAYMENT'",
            status,
            reason,
            id)
        != 1) return;
    for (var item : lines) {
      long product = Values.num(item.get("productId"));
      boolean activity = "ACTIVITY".equals(item.get("stockSource"));
      Long activityId = activity ? Values.num(item.get("activityId")) : null;
      stock.change(
          activity ? "ACTIVITY" : "PRODUCT",
          activity ? activityId : product,
          product,
          Math.toIntExact(Values.num(item.get("quantity"))),
          "ORDER_RELEASE:" + order.get("orderNo") + ":" + product,
          "ORDER_RELEASE",
          id,
          activityId,
          user);
    }
    events.log(
        "ORDER_" + status,
        "ORDER",
        id,
        user,
        Map.of("status", "PENDING_PAYMENT"),
        Map.of("status", status),
        null);
  }

  public Map<String, Object> ship(long id, long actor, Map<String, Object> b) {
    String company = Values.required(b.get("logisticsCompany"), 64),
        tracking = Values.required(b.get("trackingNo"), 64);
    return tx.run(
        () -> {
          var row = db.require("SELECT * FROM mall_order WHERE id=? FOR UPDATE", id);
          if ("SHIPPED".equals(row.get("status"))
              && company.equals(row.get("logisticsCompany"))
              && tracking.equals(row.get("trackingNo"))) return detail(id, actor, true);
          if (!"PAID".equals(row.get("status")))
            throw BusinessException.conflict("ORDER_STATE_CONFLICT", "只有已付款订单可发货");
          db.update(
              "UPDATE mall_order SET"
                  + " status='SHIPPED',logistics_company=?,tracking_no=?,shipped_at=UTC_TIMESTAMP(3),version=version+1"
                  + " WHERE id=? AND status='PAID'",
              company,
              tracking,
              id);
          events.log("ORDER_SHIP", "ORDER", id, actor, row, Map.of("trackingNo", tracking), null);
          return detail(id, actor, true);
        });
  }

  public Map<String, Object> confirm(long id, long user) {
    return tx.run(
        () -> {
          var row =
              db.require("SELECT * FROM mall_order WHERE id=? AND user_id=? FOR UPDATE", id, user);
          if ("COMPLETED".equals(row.get("status"))) return detail(id, user, false);
          if (!"SHIPPED".equals(row.get("status")))
            throw BusinessException.conflict("ORDER_STATE_CONFLICT", "订单未发货");
          db.update(
              "UPDATE mall_order SET"
                  + " status='COMPLETED',completed_at=UTC_TIMESTAMP(3),version=version+1 WHERE"
                  + " id=?",
              id);
          events.log("ORDER_COMPLETE", "ORDER", id, user, row, Map.of("status", "COMPLETED"), null);
          return detail(id, user, false);
        });
  }
}
