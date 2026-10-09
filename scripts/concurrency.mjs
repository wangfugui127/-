import { data, user, admin, key, receiver, assert, report } from "./lib.mjs";
const manager = await admin();
let p = await data(
  "POST",
  "/admin/products",
  {
    name: `并发验收${Date.now()}`,
    categoryCode: "OTHER",
    priceFen: 100,
    initialStock: 20,
    imageUrl: "/images/notebook.svg",
  },
  manager,
);
p = await data(
  "POST",
  `/admin/products/${p.id}/status`,
  { action: "ON_SALE", version: p.version },
  manager,
);
let activity = await data(
  "POST",
  "/admin/seckill-activities",
  {
    productId: p.id,
    title: "并发验收限量10件",
    activityPriceFen: 50,
    plannedStock: 10,
    startAt: new Date(Date.now() - 5000).toISOString(),
    endAt: new Date(Date.now() + 600000).toISOString(),
  },
  manager,
);
activity = await data(
  "POST",
  `/admin/seckill-activities/${activity.id}/status`,
  { action: "PUBLISH", version: activity.version },
  manager,
  key(),
);
const tokens = [];
for (let i = 0; i < 40; i++) tokens.push(await user("load"));
const latencies = [],
  results = [],
  created = [];
let next = 0;
const started = performance.now();
await Promise.all(
  Array.from({ length: 12 }, async () => {
    for (;;) {
      const i = next++;
      if (i >= tokens.length) return;
      const stamp = performance.now();
      try {
        const order = await data(
          "POST",
          `/seckill-activities/${activity.id}/orders`,
          { expectedTotalFen: 50, receiver },
          tokens[i],
          key(),
        );
        created.push({ id: order.id, token: tokens[i] });
        results.push("SUCCESS");
      } catch (e) {
        results.push(e.code ?? "UNKNOWN");
      }
      latencies.push(performance.now() - stamp);
    }
  }),
);
const duration = performance.now() - started;
const after = await data(
  "GET",
  `/seckill-activities/${activity.id}`,
  undefined,
  manager,
);
assert(
  results.filter((x) => x === "SUCCESS").length === 10,
  "限量库存没有产生恰好10个成功订单",
);
assert(Number(after.stockAvailable) === 0, "限量库存未正确归零");
for (const o of created)
  await data(
    "POST",
    `/orders/${o.id}/cancel`,
    { reason: "并发验收后归还库存" },
    o.token,
  );
const restored = await data(
  "GET",
  `/seckill-activities/${activity.id}`,
  undefined,
  manager,
);
assert(Number(restored.stockAvailable) === 10, "取消后的库存未归还");
await data(
  "POST",
  `/admin/seckill-activities/${activity.id}/status`,
  { action: "DISABLE", version: restored.version },
  manager,
  key(),
);
const disabled = await data(
  "GET",
  `/seckill-activities/${activity.id}`,
  undefined,
  manager,
);
await data(
  "POST",
  `/admin/seckill-activities/${activity.id}/status`,
  { action: "CLOSE", version: disabled.version },
  manager,
  key(),
);
const productAfter = (
  await data("GET", "/admin/products?size=50", undefined, manager)
).items.find((x) => x.id === p.id);
assert(
  Number(productAfter.stockAvailable) === 20,
  "活动关闭回收后普通库存不守恒",
);
await data(
  "POST",
  `/admin/products/${p.id}/status`,
  { action: "OFF_SALE", version: productAfter.version },
  manager,
);
latencies.sort((a, b) => a - b);
report("concurrency", {
  passed: true,
  requests: 40,
  workers: 12,
  activityStock: 10,
  success: 10,
  results: results.reduce((a, k) => ((a[k] = (a[k] ?? 0) + 1), a), {}),
  durationMs: Math.round(duration),
  throughputRps: Number(((40 * 1000) / duration).toFixed(2)),
  p50Ms: Math.round(latencies[Math.ceil(latencies.length * 0.5) - 1]),
  p95Ms: Math.round(latencies[Math.ceil(latencies.length * 0.95) - 1]),
  afterSaleStock: 0,
  afterCancelStock: 10,
  afterCloseProductStock: 20,
  note: "单机Windows Docker Desktop演示环境；只测本次40笔请求，注册登录不计入耗时，不代表生产容量。",
});
