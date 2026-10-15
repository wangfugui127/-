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

  @Autowired com.lsy.minimall.support.DatabaseFixture db;
  @MockitoSpyBean ProductMapper productMapper;
  @MockitoSpyBean com.lsy.minimall.persistence.mapper.ClockMapper clock;
  @MockitoSpyBean com.lsy.minimall.persistence.mapper.MallUserMapper userMapper;
  @Autowired com.lsy.minimall.persistence.mapper.OutboxEventMapper outbox;
  @Autowired com.lsy.minimall.persistence.mapper.OperationLogMapper operationLogs;
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
    reset(events, productMapper, clock, userMapper);
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
  void activityPoolAndQualificationRestoredAfterCancel() {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    assertEquals(7, stock());
    String originalKey = Values.id();
    var o = activities.buy(a, user, originalKey, activityBody());
    long originalId = Values.num(o.get("id"));
    assertEquals(o.get("id"), activities.detail(a, user).get("pendingOrderId"));
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
    assertEquals(false, activities.detail(a, user).get("hasPurchased"));
    assertNull(activities.detail(a, user).get("pendingOrderId"));
    assertEquals(
        1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE order_id=?", originalId));
    var replay = activities.buy(a, user, originalKey, activityBody());
    assertEquals(o.get("id"), replay.get("id"));
    assertEquals("CANCELLED", replay.get("status"));
    var next = activities.buy(a, user, Values.id(), activityBody());
    assertNotEquals(o.get("id"), next.get("id"));
    assertEquals(next.get("id"), activities.detail(a, user).get("pendingOrderId"));
    assertEquals(2, db.count("SELECT COUNT(*) FROM mall_order WHERE activity_id=?", a));
    assertEquals(1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE activity_id=?", a));
    assertEquals(
        next.get("id"),
        db.require("SELECT order_id FROM seckill_purchase WHERE activity_id=? AND user_id=?", a, user)
            .get("orderId"));
    assertEquals("CANCELLED", orders.detail(originalId, user, false).get("status"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM order_item WHERE order_id=?", originalId));
    orders.cancel(Values.num(next.get("id")), user, "再次购买后取消");
    action(a, "DISABLE");
    action(a, "CLOSE");
    assertEquals(10, stock());
  }

  @Test
  void activityTimeoutRestoresQualificationAndOldPaymentCannotAffectRepurchase() {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var original = activities.buy(a, user, Values.id(), activityBody());
    long originalId = Values.num(original.get("id"));
    var oldPay = payments.create(originalId, user, "LATE_SUCCESS", Values.id());
    db.update(
        "UPDATE mall_order SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
        originalId);
    orders.closeExpired(originalId);
    // 已关闭订单仍保留原资格行，覆盖旧版本遗留数据，不依赖清理或迁移。
    assertEquals(1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE order_id=?", originalId));
    assertEquals(false, activities.detail(a, user).get("hasPurchased"));
    assertNull(activities.detail(a, user).get("pendingOrderId"));
    var next = activities.buy(a, user, Values.id(), activityBody());
    long nextId = Values.num(next.get("id"));
    var cb = callback(oldPay, Values.id());
    payments.callback(cb);
    payments.callback(cb);
    assertEquals("REFUND_PENDING", payments.detail(Values.num(oldPay.get("id")), user).get("status"));
    assertEquals("CLOSED", orders.detail(originalId, user, false).get("status"));
    assertEquals("PENDING_PAYMENT", orders.detail(nextId, user, false).get("status"));
    assertEquals(next.get("id"), activities.detail(a, user).get("pendingOrderId"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE order_id=?", nextId));
    assertEquals(
        2, db.count("SELECT stock_available FROM seckill_activity WHERE id=?", a));
  }

  @Test
  void activityPaidShippedAndCompletedOrdersKeepQualification() {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var order = activities.buy(a, user, Values.id(), activityBody());
    long id = Values.num(order.get("id"));
    var pay = payments.create(id, user, "SUCCESS", Values.id());
    payments.callback(callback(pay, Values.id()));
    for (String state : List.of("PAID", "SHIPPED", "COMPLETED")) {
      assertEquals(state, orders.detail(id, user, false).get("status"));
      assertEquals(true, activities.detail(a, user).get("hasPurchased"));
      assertNull(activities.detail(a, user).get("pendingOrderId"));
      // 此处验证购买资格，不让多轮拒绝请求触发独立的每秒限流。
      redis.delete("rate:seckill:" + a + ":" + user);
      assertEquals(
          "PURCHASE_LIMIT_REACHED",
          assertThrows(
                  BusinessException.class,
                  () -> activities.buy(a, user, Values.id(), activityBody()))
              .code);
      if (state.equals("PAID"))
        orders.ship(id, admin, Map.of("logisticsCompany", "测试快递", "trackingNo", "TEST001"));
      else if (state.equals("SHIPPED")) orders.confirm(id, user);
    }
    assertEquals(1, db.count("SELECT COUNT(*) FROM mall_order WHERE activity_id=?", a));
    assertEquals(2, db.count("SELECT stock_available FROM seckill_activity WHERE id=?", a));
  }

  @Test
  void activityQualificationReuseRollsBackWithNewOrder() {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var original = activities.buy(a, user, Values.id(), activityBody());
    long originalId = Values.num(original.get("id"));
    orders.cancel(originalId, user, "取消后重购");
    long ledgerCount = db.count("SELECT COUNT(*) FROM inventory_ledger WHERE activity_id=?", a);
    doThrow(new IllegalStateException("模拟事件写入失败"))
        .when(events)
        .add(eq("ORDER_TIMEOUT_CHECK"), eq("ORDER"), anyLong(), anyMap(), anyInt());
    assertThrows(
        IllegalStateException.class,
        () -> activities.buy(a, user, Values.id(), activityBody()));
    assertEquals(1, db.count("SELECT COUNT(*) FROM mall_order WHERE activity_id=?", a));
    assertEquals(
        original.get("id"),
        db.require("SELECT order_id FROM seckill_purchase WHERE activity_id=?", a).get("orderId"));
    assertEquals(3, db.count("SELECT stock_available FROM seckill_activity WHERE id=?", a));
    assertEquals(ledgerCount, db.count("SELECT COUNT(*) FROM inventory_ledger WHERE activity_id=?", a));
    assertEquals(false, activities.detail(a, user).get("hasPurchased"));
    reset(events);
    var next = activities.buy(a, user, Values.id(), activityBody());
    assertEquals(next.get("id"), activities.detail(a, user).get("pendingOrderId"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void concurrentActivityRepurchaseCreatesOneOrder(boolean sameKey) throws Exception {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var original = activities.buy(a, user, Values.id(), activityBody());
    orders.cancel(Values.num(original.get("id")), user, "取消后并发重购");
    String firstKey = Values.id(), secondKey = sameKey ? firstKey : Values.id();
    var pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Object>> futures = new ArrayList<>();
      for (String key : List.of(firstKey, secondKey))
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return activities.buy(a, user, key, activityBody());
                  } catch (BusinessException e) {
                    return e;
                  }
                }));
      start.countDown();
      List<Object> outcomes = new ArrayList<>();
      for (var future : futures) outcomes.add(future.get(10, TimeUnit.SECONDS));
      long successes = outcomes.stream().filter(Map.class::isInstance).count();
      assertEquals(sameKey ? 2L : 1L, successes);
      String pendingId = Values.text(activities.detail(a, user).get("pendingOrderId"));
      for (Object outcome : outcomes) {
        if (outcome instanceof Map<?, ?> order) assertEquals(pendingId, order.get("id"));
        else assertEquals("PURCHASE_LIMIT_REACHED", ((BusinessException) outcome).code);
      }
      assertEquals(2, db.count("SELECT COUNT(*) FROM mall_order WHERE activity_id=?", a));
      assertEquals(1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE activity_id=?", a));
      assertEquals(2, db.count("SELECT stock_available FROM seckill_activity WHERE id=?", a));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void activityPendingOrderIsOnlyExposedToItsOwner() throws Exception {
    long a = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var order = activities.buy(a, user, Values.id(), activityBody());
    mvc.perform(get("/api/v1/seckill-activities/" + a).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasPurchased").value(true))
        .andExpect(jsonPath("$.data.pendingOrderId").value(order.get("id")));
    mvc.perform(get("/api/v1/seckill-activities/" + a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasPurchased").value(false))
        .andExpect(jsonPath("$.data.pendingOrderId").isEmpty());
    assertNull(activities.detail(a, admin).get("pendingOrderId"));
    orders.cancel(Values.num(order.get("id")), user, "取消");
    mvc.perform(get("/api/v1/seckill-activities/" + a).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasPurchased").value(false))
        .andExpect(jsonPath("$.data.pendingOrderId").isEmpty());
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
  void authenticatedRequestsRenewSessionForTwoHours() throws Exception {
    String sessionKey = "auth:session:" + Values.hash(token);
    redis.expire(sessionKey, Duration.ofSeconds(30));
    mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    long remaining = redis.getExpire(sessionKey);
    assertTrue(remaining >= 7195 && remaining <= 7200);
    assertEquals(Long.toString(user), redis.opsForValue().get(sessionKey));

    redis.expire(sessionKey, Duration.ofSeconds(60));
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(Long.toString(user)));
    remaining = redis.getExpire(sessionKey);
    assertTrue(remaining >= 7195 && remaining <= 7200);
  }

  @Test
  void expiredAndUnknownSessionsAreNotRecreated() throws Exception {
    String sessionKey = "auth:session:" + Values.hash(token);
    redis.expire(sessionKey, Duration.ZERO);
    mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    assertFalse(redis.hasKey(sessionKey));

    String unknownToken = Values.id();
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + unknownToken))
        .andExpect(status().isUnauthorized());
    assertFalse(redis.hasKey("auth:session:" + Values.hash(unknownToken)));
  }

  @Test
  void logoutDeletesRenewedSessionAndOldTokenRemainsInvalid() throws Exception {
    String sessionKey = "auth:session:" + Values.hash(token);
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertFalse(redis.hasKey(sessionKey));
    mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    assertFalse(redis.hasKey(sessionKey));
  }

  @Test
  void disabledAccountDoesNotRenewSession() throws Exception {
    String sessionKey = "auth:session:" + Values.hash(token);
    redis.expire(sessionKey, Duration.ofSeconds(60));
    db.update("UPDATE mall_user SET status='DISABLED' WHERE id=?", user);
    mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    long remaining = redis.getExpire(sessionKey);
    assertTrue(remaining > 0 && remaining <= 60);
  }

  @Test
  void sessionDeletedDuringIdentityCheckCannotBeRenewed() throws Exception {
    String sessionKey = "auth:session:" + Values.hash(token);
    doAnswer(
            invocation -> {
              Object row = invocation.callRealMethod();
              redis.delete(sessionKey);
              return row;
            })
        .when(userMapper)
        .findIdentityById(any());
    mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    assertFalse(redis.hasKey(sessionKey));
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
    clearInvocations(productMapper, clock);
    var detail = products.detail(product);
    assertEquals(Long.toString(product), detail.get("id"));
    assertNotNull(Instant.parse((String) detail.get("serverNow")));
    verify(productMapper, times(1)).findDetailById(product);
    verify(clock, never()).now();
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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void failedPaymentRetriesOnSameOrderWithImmutableAttemptsAndStaleCallbackIsolation(
      boolean seckill) throws Exception {
    long activity = seckill ? activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600)) : 0;
    var order = seckill ? activities.buy(activity, user, Values.id(), activityBody()) : create(1);
    long id = Values.num(order.get("id"));
    String firstKey = Values.id(), retryKey = Values.id();
    var failed = payments.create(id, user, "FAILURE", firstKey);
    payments.simulate(Values.num(failed.get("paymentId")), Values.id());
    assertEquals("FAILED", payments.detail(Values.num(failed.get("id")), user).get("status"));
    assertEquals(failed.get("id"), payments.create(id, user, "FAILURE", firstKey).get("id"));
    if (seckill) {
      assertEquals(order.get("id"), activities.detail(activity, user).get("pendingOrderId"));
      assertEquals(true, activities.detail(activity, user).get("hasPurchased"));
      assertEquals(
          "PURCHASE_LIMIT_REACHED",
          assertThrows(
                  BusinessException.class,
                  () -> activities.buy(activity, user, Values.id(), activityBody()))
              .code);
      assertEquals(1, db.count("SELECT COUNT(*) FROM mall_order WHERE activity_id=?", activity));
      assertEquals(2, db.count("SELECT stock_available FROM seckill_activity WHERE id=?", activity));
    }
    var pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                return payments.create(id, user, "SUCCESS", retryKey);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return payments.create(id, user, "SUCCESS", retryKey);
              });
      start.countDown();
      var next = a.get(10, TimeUnit.SECONDS);
      assertEquals(next.get("id"), b.get(10, TimeUnit.SECONDS).get("id"));
      assertNotEquals(failed.get("id"), next.get("id"));
      assertEquals(2, db.count("SELECT COUNT(*) FROM payment_record WHERE order_id=?", id));
      assertEquals(
          "PAYMENT_IN_PROGRESS",
          assertThrows(
                  BusinessException.class, () -> payments.create(id, user, "SUCCESS", Values.id()))
              .code);
      payments.simulate(Values.num(failed.get("id")), Values.id()); // Old outbox redelivery.
      assertEquals("CREATED", payments.detail(Values.num(next.get("id")), user).get("status"));
      assertEquals("PENDING_PAYMENT", orders.detail(id, user, false).get("status"));
      payments.callback(callback(next, Values.id()));
      assertEquals("PAID", orders.detail(id, user, false).get("status"));
      assertEquals(
          next.get("id"), ((Map<?, ?>) orders.detail(id, user, false).get("payment")).get("id"));
      if (seckill) {
        assertEquals(true, activities.detail(activity, user).get("hasPurchased"));
        assertNull(activities.detail(activity, user).get("pendingOrderId"));
        assertEquals(1, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE order_id=?", id));
        assertEquals(
            2,
            Values.num(
                db.require("SELECT stock_available FROM seckill_activity WHERE id=?", activity)
                    .get("stockAvailable")));
      } else assertEquals(9, stock());
    } finally {
      pool.shutdownNow();
    }
    mvc.perform(
            post("/api/v1/orders/" + id + "/mock-payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", firstKey)
                .contentType("application/json")
                .content("{\"scenario\":\"FAILURE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FAILED"));
  }

  @Test
  void pendingQuotaIsAtomicAcrossDifferentProductsAndStillReplaysAtCapacity() throws Exception {
    product = newProduct("充足库存", 100);
    for (int i = 0; i < 19; i++) create(1);
    List<Long> products = new ArrayList<>();
    for (int i = 0; i < 8; i++) products.add(newProduct("并发商品" + i, 10));
    CountDownLatch start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(8);
    Map<String, Object> winner = null;
    String winnerKey = null;
    long winnerProduct = 0;
    try {
      List<Future<Map<String, Object>>> jobs = new ArrayList<>();
      List<String> keys = new ArrayList<>();
      for (long p : products) {
        String key = Values.id();
        keys.add(key);
        jobs.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return orders.create(user, key, body(p, 1));
                  } catch (BusinessException e) {
                    assertEquals("PENDING_ORDER_LIMIT", e.code);
                    return null;
                  }
                }));
      }
      start.countDown();
      int success = 0;
      for (int i = 0; i < jobs.size(); i++) {
        var result = jobs.get(i).get(15, TimeUnit.SECONDS);
        if (result != null) {
          success++;
          winner = result;
          winnerKey = keys.get(i);
          winnerProduct = products.get(i);
        }
      }
      assertEquals(1, success);
      assertEquals(
          20,
          db.count(
              "SELECT COUNT(*) FROM mall_order WHERE user_id=? AND status='PENDING_PAYMENT'",
              user));
      assertEquals(
          winner.get("id"), orders.create(user, winnerKey, body(winnerProduct, 1)).get("id"));
      long activity = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
      assertEquals(
          "PENDING_ORDER_LIMIT",
          assertThrows(
                  BusinessException.class,
                  () -> activities.buy(activity, user, Values.id(), activityBody()))
              .code);
      assertEquals(
          0, db.count("SELECT COUNT(*) FROM seckill_purchase WHERE activity_id=?", activity));
      assertEquals(
          3,
          Values.num(
              db.require("SELECT stock_available FROM seckill_activity WHERE id=?", activity)
                  .get("stockAvailable")));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void invalidationFencesAReaderPausedAfterDatabaseRead() throws Exception {
    // Drain earlier creation invalidations so this race targets a later committed update.
    for (var e :
        db.rows("SELECT payload FROM outbox_event WHERE event_type='PRODUCT_CACHE_INVALIDATE'"))
      messages.consume("minimall-product-cache", Values.text(e.get("payload")));
    CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
    var once = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(
            call -> {
              Object row = call.callRealMethod();
              if (once.compareAndSet(false, true)) {
                read.countDown();
                assertTrue(resume.await(10, TimeUnit.SECONDS));
              }
              return row;
            })
        .when(productMapper)
        .findDetailById(eq(product));
    var pool = Executors.newSingleThreadExecutor();
    try {
      var load = pool.submit(() -> products.detail(product));
      assertTrue(read.await(10, TimeUnit.SECONDS));
      var latest = db.require("SELECT * FROM product WHERE id=? FOR UPDATE", product);
      products.status(product, "OFF_SALE", Values.num(latest.get("version")), admin);
      for (var e :
          db.rows("SELECT payload FROM outbox_event WHERE event_type='PRODUCT_CACHE_INVALIDATE'"))
        messages.consume("minimall-product-cache", Values.text(e.get("payload")));
      resume.countDown();
      assertEquals("ON_SALE", load.get(10, TimeUnit.SECONDS).get("status"));
      assertNull(
          redis.opsForValue().get("product:detail:" + product)); // Stale response was never cached.
      assertThrows(BusinessException.class, () -> products.detail(product));
      assertEquals("null", redis.opsForValue().get("product:detail:" + product));
    } finally {
      resume.countDown();
      pool.shutdownNow();
      reset(productMapper, clock);
    }
  }

  @Test
  void creationGateDoesNotDeadlockWithCartForeignKeyChecks() throws Exception {
    CountDownLatch productLocked = new CountDownLatch(1), creatorWaiting = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    doAnswer(
            call -> {
              if (Thread.currentThread().getName().equals("quota-order-creator"))
                creatorWaiting.countDown();
              return call.callRealMethod();
            })
        .when(productMapper)
        .findByIdForUpdate(eq(product));
    try {
      var cart =
          pool.submit(
              () ->
                  tx.run(
                      () -> {
                        db.require("SELECT * FROM product WHERE id=? FOR UPDATE", product);
                        productLocked.countDown();
                        try {
                          assertTrue(creatorWaiting.await(10, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return carts.add(user, product, 1);
                      }));
      assertTrue(productLocked.await(10, TimeUnit.SECONDS));
      var order =
          pool.submit(
              () -> {
                Thread.currentThread().setName("quota-order-creator");
                return create(1);
              });
      assertNotNull(cart.get(15, TimeUnit.SECONDS));
      assertNotNull(order.get(15, TimeUnit.SECONDS));
      assertEquals(9, stock());
    } finally {
      creatorWaiting.countDown();
      pool.shutdownNow();
      reset(productMapper, clock);
    }
  }

  @Test
  void normalAndSeckillOrdersShareTheLastPendingSlot() throws Exception {
    product = newProduct("混合订单库存", 100);
    for (int i = 0; i < 19; i++) create(1);
    long activity = activity(3, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    long other = newProduct("普通订单商品", 10);
    CountDownLatch start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> normal =
          () -> {
            start.await();
            try {
              orders.create(user, Values.id(), body(other, 1));
              return true;
            } catch (BusinessException e) {
              assertEquals("PENDING_ORDER_LIMIT", e.code);
              return false;
            }
          };
      Callable<Boolean> seckill =
          () -> {
            start.await();
            try {
              activities.buy(activity, user, Values.id(), activityBody());
              return true;
            } catch (BusinessException e) {
              assertEquals("PENDING_ORDER_LIMIT", e.code);
              return false;
            }
          };
      var a = pool.submit(normal);
      var b = pool.submit(seckill);
      start.countDown();
      assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
      assertEquals(
          20,
          db.count(
              "SELECT COUNT(*) FROM mall_order WHERE user_id=? AND status='PENDING_PAYMENT'",
              user));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void mapperPaginationFiltersAndResponseShape() {
    long second = newProduct("玻璃杯", 3);
    long third = newProduct("保温杯", 4);
    var draft =
        products.create(Map.of("name", "草稿杯", "categoryCode", "LIFE", "priceFen", 100), admin);
    var page = products.page(false, 1, 1, "LIFE", "杯", null);
    assertEquals(2L, page.get("total"));
    var items = (List<?>) page.get("items");
    assertEquals(1, items.size());
    assertEquals(Long.toString(third), ((Map<?, ?>) items.getFirst()).get("id"));
    page = products.page(false, 2, 1, "LIFE", "杯", null);
    assertEquals(
        Long.toString(second), ((Map<?, ?>) ((List<?>) page.get("items")).getFirst()).get("id"));
    page = products.page(false, 3, 1, "LIFE", "杯", null);
    assertEquals(2L, page.get("total"));
    assertTrue(((List<?>) page.get("items")).isEmpty());
    assertEquals(0L, products.page(false, 1, 20, null, "' OR 1=1 --", null).get("total"));
    var adminPage = products.page(true, 1, 20, null, null, "DRAFT");
    assertEquals(1L, adminPage.get("total"));
    assertEquals(
        draft.get("id"), ((Map<?, ?>) ((List<?>) adminPage.get("items")).getFirst()).get("id"));
    long orderId = Values.num(create(1).get("id"));
    var orderPage = orders.page(user, false, 1, 1, "PENDING_PAYMENT", null, null);
    assertEquals(1L, orderPage.get("total"));
    var orderRow = (Map<?, ?>) ((List<?>) orderPage.get("items")).getFirst();
    assertEquals(Long.toString(orderId), orderRow.get("id"));
    assertFalse(((List<?>) orderRow.get("items")).isEmpty());
  }

  @Test
  void mapperActivityPaginationRetainsJoinedFieldsAndDraftVisibility() {
    long id = activity(2, db.now().minusSeconds(1), db.now().plusSeconds(3600));
    var draft =
        activities.create(
            Map.of(
                "productId",
                product,
                "title",
                "活动草稿",
                "activityPriceFen",
                1990,
                "plannedStock",
                1,
                "startAt",
                db.now().toString(),
                "endAt",
                db.now().plusSeconds(7200).toString()),
            admin);
    var page = activities.page(false, 1, 20, null);
    assertEquals(1L, page.get("total"));
    var row = (Map<?, ?>) ((List<?>) page.get("items")).getFirst();
    assertEquals(Long.toString(id), row.get("id"));
    assertEquals(Long.toString(product), row.get("productId"));
    assertNotNull(row.get("productName"));
    assertNotNull(row.get("originalPriceFen"));
    page = activities.page(true, 1, 20, "DRAFT");
    assertEquals(1L, page.get("total"));
    assertEquals(draft.get("id"), ((Map<?, ?>) ((List<?>) page.get("items")).getFirst()).get("id"));
  }

  @Test
  void mapperGeneratedIdsPasswordLoginAndNullableResponseFields() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"MapperBuyer\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").isString())
        .andExpect(jsonPath("$.data.username").value("mapperbuyer"));
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"mapperbuyer\",\"password\":\"password123\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"MAPPERBUYER\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.token").isString())
        .andExpect(jsonPath("$.data.user.role").value("USER"));
    var order = create(1);
    assertTrue(order.containsKey("paidAt"));
    assertNull(order.get("paidAt"));
    assertNotNull(Instant.parse((String) order.get("createdAt")));
    var payment = payments.create(Values.num(order.get("id")), user, "SUCCESS", Values.id());
    var summary =
        (Map<?, ?>) orders.detail(Values.num(order.get("id")), user, false).get("payment");
    assertEquals(payment.get("paymentId"), summary.get("id"));
    assertTrue(summary.containsKey("refundedAt"));
    assertNull(summary.get("refundedAt"));
  }

  @Test
  void mapperOutboxLeaseRecoveryAndOwnerFences() {
    db.update("DELETE FROM outbox_event");
    events.add("ORDER_TIMEOUT_CHECK", "ORDER", 123, Map.of("orderId", "123"), 0);
    var event =
        tx.run(
            () -> {
              var batch = outbox.claimBatch();
              assertEquals(1, batch.size());
              var row = batch.getFirst();
              assertEquals(1, outbox.markSending("owner-one", row.get("id")));
              return row;
            });
    Object id = event.get("id");
    assertEquals(0, outbox.markPublished(id, "wrong-owner"));
    assertEquals(1, outbox.markRetry("PENDING", 0, "test retry", id, "owner-one"));
    tx.run(
        () -> {
          assertEquals(1, outbox.claimBatch().size());
          assertEquals(1, outbox.markSending("owner-two", id));
        });
    db.update(
        "UPDATE outbox_event SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
        id);
    tx.run(
        () -> {
          assertEquals(1, outbox.claimBatch().size());
          assertEquals(1, outbox.markSending("owner-three", id));
        });
    assertEquals(0, outbox.markPublished(id, "owner-two"));
    assertEquals(1, outbox.markPublished(id, "owner-three"));
    var stored = db.require("SELECT * FROM outbox_event WHERE id=?", id);
    assertEquals("PUBLISHED", stored.get("status"));
    assertEquals(3L, Values.num(stored.get("attempts")));
    assertNull(stored.get("leaseUntil"));
    assertNotNull(stored.get("publishedAt"));
  }

  @Test
  void mapperKeepsRowsWhoseSelectedColumnIsNull() {
    String key = Values.id();
    events.log("NULL_PROJECTION", "PRODUCT", product, admin, null, null, key);
    var row = operationLogs.findIdempotentResult(admin, "NULL_PROJECTION", key);
    assertNotNull(row);
    assertTrue(row.containsKey("afterState"));
    assertNull(row.get("afterState"));
  }

  @Test
  void mapperDatabaseClockIsFreshWithinOneTransaction() {
    tx.run(
        () -> {
          Instant first = clock.now();
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          assertTrue(clock.now().isAfter(first));
        });
  }
}
