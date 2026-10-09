package com.lsy.minimall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lsy.minimall.cart.*;
import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.messaging.*;
import com.lsy.minimall.order.*;
import com.lsy.minimall.payment.*;
import com.lsy.minimall.product.*;
import com.lsy.minimall.seckill.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
      "app.messaging.enabled=false",
      "app.jobs.enabled=false",
      "app.bootstrap.seed-demo=false",
      "app.bootstrap.admin-password=",
      "app.mock-payment.secret=test-secret-32-characters-minimum-long",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class CommerceIT {
  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("minimall_test")
          .withUsername("test")
          .withPassword("test-password")
          .withCommand("--default-time-zone=+00:00");

  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redisContainer::getHost);
    r.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    r.add("spring.data.redis.password", () -> "");
  }

  @Autowired Db db;
  @Autowired Transactions tx;
  @Autowired ProductService products;
  @Autowired CartService carts;
  @Autowired OrderService orders;
  @Autowired PaymentService payments;
  @Autowired ActivityService activities;
  @Autowired MessageService messages;
  @Autowired StringRedisTemplate redis;
  @Autowired MockMvc mvc;
  @MockitoSpyBean Events events;
  long admin, user, product;
  String token;

  @BeforeEach
  void setup() {
    reset(events);
    for (String t :
        List.of(
            "consumer_event",
            "outbox_event",
            "operation_log",
            "inventory_ledger",
            "seckill_purchase",
            "payment_record",
            "order_item",
            "mall_order",
            "cart_item",
            "seckill_activity",
            "product",
            "mall_user")) db.update("DELETE FROM " + t);
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    admin = newUser("admin", true);
    user = newUser("buyer", false);
    product = newProduct("测试商品", 10);
    token = Values.id();
    redis
        .opsForValue()
        .set("auth:session:" + Values.hash(token), Long.toString(user), Duration.ofHours(1));
  }

  long newUser(String name, boolean admin) {
    return db.insert(
        "INSERT INTO mall_user(username,password_hash,role) VALUES (?,? ,?)",
        name,
        "not-used-in-http-test",
        admin ? "ADMIN" : "USER");
  }

  long newProduct(String name, int stock) {
    var p =
        products.create(
            Map.of(
                "name",
                name,
                "categoryCode",
                "LIFE",
                "specification",
                "测试",
                "description",
                "测试商品描述",
                "imageUrl",
                "/images/cup.svg",
                "priceFen",
                1990,
                "initialStock",
                stock),
            admin);
    products.status(Values.num(p.get("id")), "ON_SALE", Values.num(p.get("version")), admin);
    return Values.num(p.get("id"));
  }

  Map<String, Object> body(long product, int qty) {
    return Map.of(
        "source",
        "DIRECT",
        "items",
        List.of(Map.of("productId", Long.toString(product), "quantity", qty)),
        "cartItemIds",
        List.of(),
        "expectedTotalFen",
        1990L * qty,
        "receiver",
        Map.of("name", "测试用户", "phone", "13800000001", "address", "测试地址一号楼"));
  }

  Map<String, Object> create(int qty) {
    return orders.create(user, Values.id(), body(product, qty));
  }

  long stock() {
    return Values.num(
        db.require("SELECT stock_available FROM product WHERE id=?", product)
            .get("stockAvailable"));
  }

  Map<String, Object> callback(Map<String, Object> pay, String cb) {
    return Map.of(
        "callbackId",
        cb,
        "paymentNo",
        pay.get("paymentNo"),
        "providerTradeNo",
        "MOCK:" + pay.get("paymentNo"),
        "amountFen",
        pay.get("amountFen"),
        "currency",
        "CNY",
        "result",
        "SUCCESS",
        "occurredAt",
        "2026-10-09T00:00:00Z");
  }

  long activity(int quantity, Instant start, Instant end) {
    var a =
        activities.create(
            Map.of(
                "productId",
                Long.toString(product),
                "title",
                "测试限量活动",
                "activityPriceFen",
                990,
                "plannedStock",
                quantity,
                "startAt",
                start.toString(),
                "endAt",
                end.toString()),
            admin);
    activities.action(
        Values.num(a.get("id")),
        admin,
        Values.id(),
        Map.of("action", "PUBLISH", "version", a.get("version")));
    return Values.num(a.get("id"));
  }

  Map<String, Object> activityBody() {
    return Map.of(
        "expectedTotalFen",
        990,
        "receiver",
        Map.of("name", "测试用户", "phone", "13800000001", "address", "测试地址一号楼"));
  }

  void action(long id, String action) {
    var a = db.require("SELECT * FROM seckill_activity WHERE id=?", id);
    activities.action(
        id, admin, Values.id(), Map.of("action", action, "version", a.get("version")));
  }

  @Test
  void databaseClockAndJdbcDateTimesAreUtc() {
    assertTrue(Math.abs(Duration.between(db.now(), Instant.now()).toSeconds()) < 5);
    var o = create(1);
    long remaining =
        Duration.between(Instant.now(), Instant.parse(Values.text(o.get("expiresAt")))).toSeconds();
    assertTrue(remaining >= 590 && remaining <= 601);
  }

  @Test
  void ordinaryOrderAndCancellationAreAtomic() {
    var o = create(2);
    assertEquals(8, stock());
    assertEquals(3980L, Values.num(o.get("payableFen")));
    long id = Values.num(o.get("id"));
    orders.cancel(id, user, "取消");
    orders.cancel(id, user, "再次取消");
    orders.closeExpired(id);
    assertEquals(10, stock());
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM inventory_ledger WHERE order_id=? AND reason='ORDER_RELEASE'",
            id));
  }

  @Test
  void transactionRollsBackWhenOutboxInsertFails() {
    doThrow(new RuntimeException("injected outbox failure"))
        .when(events)
        .add(eq("ORDER_TIMEOUT_CHECK"), eq("ORDER"), anyLong(), anyMap(), anyInt());
    assertThrows(RuntimeException.class, () -> create(2));
    assertEquals(10, stock());
    assertEquals(0, db.count("SELECT COUNT(*) FROM mall_order"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM inventory_ledger WHERE reason='ORDER_RESERVE'"));
  }

  @Test
  void multiProductFailureLeavesNoPartialOrder() {
    long second = newProduct("缺货商品", 0);
    var b =
        Map.<String, Object>of(
            "source",
            "DIRECT",
            "items",
            List.of(
                Map.of("productId", Long.toString(product), "quantity", 2),
                Map.of("productId", Long.toString(second), "quantity", 1)),
            "cartItemIds",
            List.of(),
            "expectedTotalFen",
            5970,
            "receiver",
            body(product, 1).get("receiver"));
    assertThrows(BusinessException.class, () -> orders.create(user, Values.id(), b));
    assertEquals(10, stock());
    assertEquals(0, db.count("SELECT COUNT(*) FROM mall_order"));
  }

  @Test
  void concurrentSameKeyCreatesOneOrder() throws Exception {
    String key = Values.id();
    var pool = Executors.newFixedThreadPool(12);
    try {
      var futures = new ArrayList<Future<String>>();
      var latch = new CountDownLatch(1);
      for (int i = 0; i < 12; i++)
        futures.add(
            pool.submit(
                () -> {
                  latch.await();
                  return Values.text(orders.create(user, key, body(product, 1)).get("id"));
                }));
      latch.countDown();
      Set<String> ids = new HashSet<>();
      for (var f : futures) ids.add(f.get(20, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(9, stock());
      assertEquals(1, db.count("SELECT COUNT(*) FROM mall_order"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void keyConflictRejectedWithoutMutation() {
    String key = Values.id();
    orders.create(user, key, body(product, 1));
    var e = assertThrows(BusinessException.class, () -> orders.create(user, key, body(product, 2)));
    assertEquals("IDEMPOTENCY_CONFLICT", e.code);
    assertEquals(9, stock());
  }

  @Test
  void competingBuyersCannotOversell() throws Exception {
    var users = new ArrayList<Long>();
    for (int i = 0; i < 30; i++) users.add(newUser("concurrent_" + i, false));
    var pool = Executors.newFixedThreadPool(30);
    try {
      CountDownLatch latch = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (long buyer : users)
        results.add(
            pool.submit(
                () -> {
                  latch.await();
                  try {
                    orders.create(buyer, Values.id(), body(product, 1));
                    return true;
                  } catch (BusinessException e) {
                    assertEquals("STOCK_NOT_ENOUGH", e.code);
                    return false;
                  }
                }));
      latch.countDown();
      int successes = 0;
      for (var f : results) if (f.get(30, TimeUnit.SECONDS)) successes++;
      assertEquals(10, successes);
      assertEquals(0, stock());
      assertEquals(10, db.count("SELECT COUNT(*) FROM mall_order"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void duplicatePaymentAndCallbackAreIdempotent() {
    var o = create(1);
    long id = Values.num(o.get("id"));
    var pay = payments.create(id, user, "SUCCESS");
    assertEquals(pay.get("id"), payments.create(id, user, "SUCCESS").get("id"));
    var b = callback(pay, Values.id());
    payments.callback(b);
    payments.callback(b);
    assertEquals("PAID", orders.detail(id, user, false).get("status"));
    assertEquals(9, stock());
    assertEquals(1, db.count("SELECT COUNT(*) FROM payment_record"));
  }

  @Test
  void lateSuccessfulPaymentIsRefundedWithoutStockDuplication() {
    var o = create(1);
    long id = Values.num(o.get("id"));
    var pay = payments.create(id, user, "SUCCESS");
    db.update(
        "UPDATE mall_order SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?", id);
    var b = callback(pay, Values.id());
    payments.callback(b);
    assertEquals("CLOSED", orders.detail(id, user, false).get("status"));
    assertEquals("REFUND_PENDING", payments.detail(Values.num(pay.get("id")), user).get("status"));
    payments.refund(Values.num(pay.get("id")));
    payments.refund(Values.num(pay.get("id")));
    payments.callback(b);
    orders.closeExpired(id);
    assertEquals("REFUNDED", payments.detail(Values.num(pay.get("id")), user).get("status"));
    assertEquals(10, stock());
  }

  @Test
  void callbackForgeryDoesNotPay() {
    var o = create(1);
    long id = Values.num(o.get("id"));
    var pay = payments.create(id, user, "SUCCESS");
    var b = new HashMap<>(callback(pay, Values.id()));
    b.put("amountFen", 1);
    assertThrows(BusinessException.class, () -> payments.callback(b));
    assertEquals("PENDING_PAYMENT", orders.detail(id, user, false).get("status"));
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String raw = Values.json(callback(pay, Values.id()));
    payments.verify(timestamp, payments.sign(timestamp, raw), raw);
    assertThrows(
        BusinessException.class,
        () -> payments.verify(timestamp, payments.sign(timestamp, raw), raw + " "));
  }

  @Test
  void paymentAndCancelRaceHasOneCoherentOutcome() throws Exception {
    var o = create(1);
    long id = Values.num(o.get("id"));
    var pay = payments.create(id, user, "SUCCESS");
    var b = callback(pay, Values.id());
    var pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      var f1 =
          pool.submit(
              () -> {
                start.await();
                payments.callback(b);
                return true;
              });
      var f2 =
          pool.submit(
              () -> {
                start.await();
                try {
                  orders.cancel(id, user, "竞争取消");
                } catch (BusinessException e) {
                  assertEquals("ORDER_STATE_CONFLICT", e.code);
                }
                return true;
              });
      start.countDown();
      f1.get(20, TimeUnit.SECONDS);
      f2.get(20, TimeUnit.SECONDS);
      String state = Values.text(orders.detail(id, user, false).get("status"));
      if (state.equals("PAID")) {
        assertEquals(9, stock());
        assertEquals("SUCCEEDED", payments.detail(Values.num(pay.get("id")), user).get("status"));
      } else {
        assertEquals("CANCELLED", state);
        assertEquals(10, stock());
        assertEquals(
            "REFUND_PENDING", payments.detail(Values.num(pay.get("id")), user).get("status"));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void fulfillmentDoesNotDeductTwice() {
    var o = create(2);
    long id = Values.num(o.get("id"));
    var pay = payments.create(id, user, "SUCCESS");
    payments.callback(callback(pay, Values.id()));
    orders.ship(id, admin, Map.of("logisticsCompany", "测试快递", "trackingNo", "TEST001"));
    orders.ship(id, admin, Map.of("logisticsCompany", "测试快递", "trackingNo", "TEST001"));
    orders.confirm(id, user);
    orders.confirm(id, user);
    assertEquals("COMPLETED", orders.detail(id, user, false).get("status"));
    assertEquals(8, stock());
  }

  @Test
  void activityPoolAndLifetimeLimit() {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    assertEquals(7, stock());
    var o = activities.buy(a, user, Values.id(), activityBody());
    assertEquals(
        2,
        Values.num(
            db.require("SELECT stock_available FROM seckill_activity WHERE id=?", a)
                .get("stockAvailable")));
    orders.cancel(Values.num(o.get("id")), user, "取消");
    assertEquals(
        3,
        Values.num(
            db.require("SELECT stock_available FROM seckill_activity WHERE id=?", a)
                .get("stockAvailable")));
    var e =
        assertThrows(
            BusinessException.class, () -> activities.buy(a, user, Values.id(), activityBody()));
    assertEquals("PURCHASE_LIMIT_REACHED", e.code);
    action(a, "DISABLE");
    action(a, "CLOSE");
    assertEquals(10, stock());
  }

  @Test
  void activityTimeAndCloseRulesAreEnforced() {
    long future = activity(2, db.now().plusSeconds(300), db.now().plusSeconds(3600));
    assertEquals(
        "ACTIVITY_NOT_STARTED",
        assertThrows(
                BusinessException.class,
                () -> activities.buy(future, user, Values.id(), activityBody()))
            .code);
    action(future, "DISABLE");
    action(future, "CLOSE");
    long live = activity(2, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    activities.buy(live, user, Values.id(), activityBody());
    action(live, "DISABLE");
    assertEquals(
        "ACTIVITY_PENDING_ORDERS",
        assertThrows(BusinessException.class, () -> action(live, "CLOSE")).code);
  }

  @Test
  void hotActivityCannotOversell() throws Exception {
    long a = activity(5, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var users = new ArrayList<Long>();
    for (int i = 0; i < 20; i++) users.add(newUser("hot_" + i, false));
    var pool = Executors.newFixedThreadPool(20);
    try {
      CountDownLatch latch = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (long buyer : users)
        results.add(
            pool.submit(
                () -> {
                  latch.await();
                  try {
                    activities.buy(a, buyer, Values.id(), activityBody());
                    return true;
                  } catch (BusinessException e) {
                    assertEquals("STOCK_NOT_ENOUGH", e.code);
                    return false;
                  }
                }));
      latch.countDown();
      int successes = 0;
      for (var f : results) if (f.get(30, TimeUnit.SECONDS)) successes++;
      assertEquals(5, successes);
      assertEquals(
          0,
          Values.num(
              db.require("SELECT stock_available FROM seckill_activity WHERE id=?", a)
                  .get("stockAvailable")));
      assertEquals(5, stock());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void consumerFailureRollsBackBusinessAndDedup() {
    var o = create(1);
    long id = Values.num(o.get("id"));
    db.update(
        "UPDATE mall_order SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?", id);
    String message =
        Values.json(
            Map.of(
                "eventId",
                Values.id(),
                "schemaVersion",
                1,
                "eventType",
                "ORDER_TIMEOUT_CHECK",
                "data",
                Map.of("orderId", Long.toString(id))));
    doThrow(new RuntimeException("injected consumer failure"))
        .when(events)
        .log(eq("ORDER_CLOSED"), eq("ORDER"), eq(id), isNull(), any(), any(), isNull());
    assertThrows(RuntimeException.class, () -> messages.consume("test-timeout", message));
    assertEquals("PENDING_PAYMENT", orders.detail(id, user, false).get("status"));
    assertEquals(9, stock());
    assertEquals(
        0, db.count("SELECT COUNT(*) FROM consumer_event WHERE consumer_name='test-timeout'"));
    reset(events);
    messages.consume("test-timeout", message);
    messages.consume("test-timeout", message);
    assertEquals(10, stock());
    assertEquals(
        1, db.count("SELECT COUNT(*) FROM consumer_event WHERE consumer_name='test-timeout'"));
  }

  @Test
  void cartSettlementAndOwnership() {
    var c = carts.add(user, product, 2);
    long other = newUser("another", false);
    assertThrows(BusinessException.class, () -> carts.change(other, Values.num(c.get("id")), 1));
    var b =
        Map.<String, Object>of(
            "source",
            "CART",
            "items",
            List.of(),
            "cartItemIds",
            List.of(c.get("id")),
            "expectedTotalFen",
            3980,
            "receiver",
            body(product, 1).get("receiver"));
    orders.create(user, Values.id(), b);
    assertEquals(0, carts.list(user).size());
    assertEquals(8, stock());
  }

  @Test
  void stockAdjustmentIdempotencyAndZeroStock() {
    String key = Values.id();
    var b = Map.<String, Object>of("delta", 3, "reason", "盘点补充");
    products.adjust(product, b, key, admin);
    products.adjust(product, b, key, admin);
    assertEquals(13, stock());
    assertThrows(
        BusinessException.class,
        () -> products.adjust(product, Map.of("delta", 4, "reason", "不同内容"), key, admin));
    assertThrows(
        BusinessException.class,
        () -> products.adjust(product, Map.of("delta", -14, "reason", "超量扣减"), Values.id(), admin));
    assertEquals(13, stock());
    newProduct("零库存", 0);
  }

  @Test
  void permissionAndJsonIdChecks() throws Exception {
    var o = create(1);
    mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/orders/" + o.get("id")).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").isString());
    long other = newUser("other_user", false);
    String otherToken = Values.id();
    redis.opsForValue().set("auth:session:" + Values.hash(otherToken), Long.toString(other));
    mvc.perform(
            get("/api/v1/orders/" + o.get("id")).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void malformedHttpContractReturnsClientErrors() throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/orders/not-a-number").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(put("/api/v1/orders").header("Authorization", "Bearer " + token))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(get("/api/v1/unknown-resource").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void productCacheNullMarkerAndRefresh() {
    assertEquals(Long.toString(product), products.detail(product).get("id"));
    assertNotNull(redis.opsForValue().get("product:detail:" + product));
    var row = db.require("SELECT * FROM product WHERE id=?", product);
    products.status(product, "OFF_SALE", Values.num(row.get("version")), admin);
    // Delivered cache invalidation removes both positive and null cache entries.
    var pending =
        db.rows("SELECT payload FROM outbox_event WHERE event_type='PRODUCT_CACHE_INVALIDATE'");
    for (var e : pending) messages.consume("minimall-product-cache", Values.text(e.get("payload")));
    assertThrows(BusinessException.class, () -> products.detail(product));
    assertEquals("null", redis.opsForValue().get("product:detail:" + product));
  }
}
