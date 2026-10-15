package com.lsy.minimall.seckill;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.inventory.InventoryService;
import com.lsy.minimall.order.OrderService;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.mapper.*;
import com.lsy.minimall.product.ProductMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {
  private final ClockMapper clock;
  private final MallOrderMapper orderMapper;
  private final ProductMapper products;
  private final SeckillActivityMapper activityMapper;
  private final SeckillPurchaseMapper purchases;
  private final Transactions tx;
  private final InventoryService stock;
  private final OrderService orders;
  private final Events events;
  private final Operations ops;
  private final StringRedisTemplate redis;

  public ActivityService(
      ClockMapper clock,
      MallOrderMapper orderMapper,
      ProductMapper products,
      SeckillActivityMapper activityMapper,
      SeckillPurchaseMapper purchases,
      Transactions tx,
      InventoryService stock,
      OrderService orders,
      Events events,
      Operations ops,
      StringRedisTemplate redis) {
    this.clock = clock;
    this.orderMapper = orderMapper;
    this.products = products;
    this.activityMapper = activityMapper;
    this.purchases = purchases;
    this.tx = tx;
    this.stock = stock;
    this.orders = orders;
    this.events = events;
    this.ops = ops;
    this.redis = redis;
  }

  public Map<String, Object> page(boolean admin, int page, int size, String status) {
    page = Math.max(1, page);
    size = Values.size(size);
    var result =
        activityMapper.findPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
            admin,
            status);
    return Map.of(
        "items",
        MapperRows.list(result.getRecords()),
        "total",
        result.getTotal(),
        "page",
        page,
        "size",
        size);
  }

  public Map<String, Object> detail(long id, Long user) {
    var a = MapperRows.require(activityMapper.findPublicDetail(id));
    a.put("serverNow", clock.now().toString());
    var purchase = user == null ? null : purchases.findActiveByActivityAndUser(id, user);
    a.put("hasPurchased", purchase != null);
    a.put(
        "pendingOrderId",
        purchase != null && "PENDING_PAYMENT".equals(purchase.get("orderStatus"))
            ? purchase.get("orderId")
            : null);
    return a;
  }

  public Map<String, Object> create(Map<String, Object> b, long actor) {
    validate(b);
    return tx.run(
        () -> {
          MapperRows.require(products.findIdentityById(Values.num(b.get("productId"))));
          long id =
              activityMapper.createActivity(
                  Values.num(b.get("productId")),
                  b.get("title"),
                  Values.num(b.get("activityPriceFen")),
                  Values.num(b.get("plannedStock")),
                  Timestamp.from(Instant.parse(Values.text(b.get("startAt")))),
                  Timestamp.from(Instant.parse(Values.text(b.get("endAt")))));
          var a = MapperRows.require(activityMapper.findById(id));
          events.log("ACTIVITY_CREATE", "ACTIVITY", id, actor, null, a, null);
          return a;
        });
  }

  public Map<String, Object> edit(long id, Map<String, Object> b, long actor) {
    validate(b);
    return tx.run(
        () -> {
          var initial = MapperRows.require(activityMapper.findProductIdentityById(id));
          var locks = new TreeSet<Long>();
          locks.add(Values.num(initial.get("productId")));
          locks.add(Values.num(b.get("productId")));
          for (long product : locks)
            MapperRows.require(products.findIdentityByIdForUpdate(product));
          var a = MapperRows.require(activityMapper.findByIdForUpdate(id));
          if (!"DRAFT".equals(a.get("status")))
            throw BusinessException.conflict("ACTIVITY_STATE_CONFLICT", "只能修改草稿");
          if (Values.num(a.get("version")) != Values.num(b.get("version")))
            throw BusinessException.conflict("VERSION_CONFLICT", "活动已被修改");
          activityMapper.editActivity(
              Values.num(b.get("productId")),
              b.get("title"),
              Values.num(b.get("activityPriceFen")),
              Values.num(b.get("plannedStock")),
              Timestamp.from(Instant.parse(Values.text(b.get("startAt")))),
              Timestamp.from(Instant.parse(Values.text(b.get("endAt")))),
              id);
          events.log("ACTIVITY_EDIT", "ACTIVITY", id, actor, a, b, null);
          return MapperRows.require(activityMapper.findById(id));
        });
  }

  public Map<String, Object> action(long id, long actor, String key, Map<String, Object> b) {
    String action = Values.required(b.get("action"), 16);
    if (!Set.of("PUBLISH", "DISABLE", "CLOSE").contains(action))
      throw BusinessException.bad("活动动作无效");
    return ops.execute(
        actor,
        "ACTIVITY_" + action,
        "ACTIVITY",
        id,
        key,
        b,
        () -> {
          var initial = MapperRows.require(activityMapper.findProductIdentityById(id));
          long product = Values.num(initial.get("productId"));
          var p = MapperRows.require(products.findByIdForUpdate(product));
          var a = MapperRows.require(activityMapper.findByIdForUpdate(id));
          if (product != Values.num(a.get("productId")))
            throw BusinessException.conflict("VERSION_CONFLICT", "活动商品已被更改，请刷新");
          if (Values.num(a.get("version")) != Values.num(b.get("version")))
            throw BusinessException.conflict("VERSION_CONFLICT", "活动已被修改");
          String state = Values.text(a.get("status"));
          if (action.equals("PUBLISH")) {
            if (!state.equals("DRAFT")
                || !"ON_SALE".equals(p.get("status"))
                || !clock.now().isBefore(Instant.parse(Values.text(a.get("endAt")))))
              throw BusinessException.conflict("ACTIVITY_STATE_CONFLICT", "当前活动不可发布");
            if (activityMapper.countOverlapping(product, id, a.get("endAt"), a.get("startAt")) > 0)
              throw BusinessException.conflict("ACTIVITY_OVERLAP", "同商品活动时间重叠");
            int n = Math.toIntExact(Values.num(a.get("plannedStock")));
            activityMapper.publishActivity(n, id);
            stock.change(
                "PRODUCT",
                product,
                product,
                -n,
                "ACTIVITY_PUBLISH_OUT:" + id,
                "ACTIVITY_ALLOCATE",
                null,
                id,
                actor);
            stock.change(
                "ACTIVITY",
                id,
                product,
                n,
                "ACTIVITY_PUBLISH_IN:" + id,
                "ACTIVITY_ALLOCATE",
                null,
                id,
                actor);
          } else if (action.equals("DISABLE")) {
            if (!Set.of("PUBLISHED", "ENDED").contains(state))
              throw BusinessException.conflict("ACTIVITY_STATE_CONFLICT", "活动不可停用");
            activityMapper.disableActivity(id);
          } else {
            if (!Set.of("DISABLED", "ENDED").contains(state))
              throw BusinessException.conflict("ACTIVITY_STATE_CONFLICT", "先停用或等待活动结束");
            if (orderMapper.countPendingByActivity(id) > 0)
              throw BusinessException.conflict("ACTIVITY_PENDING_ORDERS", "活动仍有待付款订单");
            int n = Math.toIntExact(Values.num(a.get("stockAvailable")));
            if (n > 0) {
              stock.change(
                  "ACTIVITY",
                  id,
                  product,
                  -n,
                  "ACTIVITY_CLOSE_OUT:" + id,
                  "ACTIVITY_RETURN",
                  null,
                  id,
                  actor);
              stock.change(
                  "PRODUCT",
                  product,
                  product,
                  n,
                  "ACTIVITY_CLOSE_IN:" + id,
                  "ACTIVITY_RETURN",
                  null,
                  id,
                  actor);
            }
            activityMapper.closeActivity(id);
          }
          return MapperRows.require(activityMapper.findById(id));
        });
  }

  public Map<String, Object> buy(long id, long user, String key, Map<String, Object> b) {
    Values.requestKey(key);
    var rec = orders.receiver(b);
    long expected = Values.num(b.get("expectedTotalFen"));
    String hash =
        Values.hash(
            Values.json(
                Map.of(
                    "type",
                    "SECKILL",
                    "activityId",
                    id,
                    "receiver",
                    rec,
                    "expectedTotalFen",
                    expected)));
    var old = orders.existing(user, key, hash);
    if (old != null) return old;
    rate(id, user);
    try {
      return tx.run(
          () -> {
            orders.lockOrderCreator(user);
            var replay = orders.existing(user, key, hash);
            if (replay != null) return replay;
            orders.requirePendingCapacity(user);
            // 资格写入都在用户下单锁内；先锁旧订单，再锁商品/活动，和取消的锁顺序一致。
            var purchase = purchases.findByActivityAndUser(id, user);
            if (purchase != null) {
              var previousOrder =
                  MapperRows.require(
                      orderMapper.findByIdAndUserForUpdate(purchase.get("orderId"), user));
              if (!Set.of("CANCELLED", "CLOSED").contains(Values.text(previousOrder.get("status"))))
                throw BusinessException.conflict(
                    "PURCHASE_LIMIT_REACHED",
                    "PENDING_PAYMENT".equals(previousOrder.get("status"))
                        ? "本活动已有待付款订单，请前往原订单继续支付"
                        : "本活动已购买，不能重复购买");
            }
            var locate = MapperRows.require(activityMapper.findProductIdentityById(id));
            long productId = Values.num(locate.get("productId"));
            var product = MapperRows.require(products.findByIdForUpdate(productId));
            var a = MapperRows.require(activityMapper.findByIdForUpdate(id));
            if (productId != Values.num(a.get("productId")))
              throw BusinessException.conflict("VERSION_CONFLICT", "活动已更新");
            Instant now = clock.now();
            if (now.isBefore(Instant.parse(Values.text(a.get("startAt")))))
              throw BusinessException.conflict("ACTIVITY_NOT_STARTED", "活动尚未开始");
            if (!now.isBefore(Instant.parse(Values.text(a.get("endAt"))))
                || !"PUBLISHED".equals(a.get("status")))
              throw BusinessException.conflict("ACTIVITY_ENDED", "活动已结束或停用");
            if (!"ON_SALE".equals(product.get("status")))
              throw BusinessException.conflict("PRODUCT_UNAVAILABLE", "商品已下架");
            if (expected != Values.num(a.get("activityPriceFen")))
              throw BusinessException.conflict("PRICE_CHANGED", "请刷新活动价格");
            long orderId = orders.insertOrder(user, key, hash, "SECKILL", id, expected, rec);
            orders.insertItem(orderId, product, 1, expected, "ACTIVITY", id);
            if (purchase == null) {
              purchases.recordPurchase(id, user, orderId);
            } else if (purchases.replaceReleasedPurchase(id, user, purchase.get("orderId"), orderId)
                != 1) {
              throw BusinessException.conflict("VERSION_CONFLICT", "活动资格已更新，请刷新重试");
            }
            var o = MapperRows.require(orderMapper.findOrderNoById(orderId));
            stock.change(
                "ACTIVITY",
                id,
                productId,
                -1,
                "ORDER_RESERVE:" + o.get("orderNo") + ":" + productId,
                "ORDER_RESERVE",
                orderId,
                id,
                user);
            orders.afterCreate(orderId, user);
            return orders.detail(orderId, user, false);
          });
    } catch (DuplicateKeyException e) {
      old = orders.existing(user, key, hash);
      if (old != null) return old;
      if (purchases.countByActivityAndUser(id, user) > 0)
        throw BusinessException.conflict("PURCHASE_LIMIT_REACHED", "本活动资格已使用");
      throw e;
    }
  }

  private void rate(long activity, long user) {
    String lua =
        "local t=redis.call('TIME');local"
            + " now=tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000);redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now-1000);if"
            + " redis.call('ZCARD',KEYS[1])>=3 then return 0"
            + " end;redis.call('ZADD',KEYS[1],now,ARGV[1]);redis.call('PEXPIRE',KEYS[1],2000);return"
            + " 1";
    try {
      Long result =
          redis.execute(
              new DefaultRedisScript<>(lua, Long.class),
              List.of("rate:seckill:" + activity + ":" + user),
              Values.id());
      if (!Long.valueOf(1).equals(result))
        throw new BusinessException("RATE_LIMITED", "操作频繁，请一秒后重试", 429);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException("DEPENDENCY_UNAVAILABLE", "抢购保护服务暂不可用", 503);
    }
  }

  private void validate(Map<String, Object> b) {
    Values.num(b.get("productId"));
    Values.required(b.get("title"), 100);
    Values.money(Values.num(b.get("activityPriceFen")), 1);
    long n = Values.num(b.get("plannedStock"));
    if (n <= 0 || n > 1_000_000) throw BusinessException.bad("活动库存额度不正确");
    Instant start = Instant.parse(Values.text(b.get("startAt"))),
        end = Instant.parse(Values.text(b.get("endAt")));
    if (!start.isBefore(end)) throw BusinessException.bad("活动结束时间必须晚于开始时间");
  }
}
