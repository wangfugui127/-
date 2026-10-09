import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { admin, data, assert, report, root, sleep } from "./lib.mjs";
// Run smoke.mjs in Docker mode first to produce this fixture.
const fixture = JSON.parse(
  readFileSync(new URL("../artifacts/smoke.json", import.meta.url), "utf8"),
);
assert(
  fixture.base.endsWith(":8088"),
  "请先以BASE_URL=http://127.0.0.1:8088运行smoke.mjs",
);
const token = await admin(),
  before = await data(
    "GET",
    `/admin/orders/${fixture.orderId}`,
    undefined,
    token,
  );
assert(before.status === "COMPLETED", "验收订单必须已完成");
const compose = (...args) =>
  execFileSync(
    "docker",
    [
      "compose",
      "-p",
      "minimall-docker",
      "--env-file",
      "deploy/.env",
      "-f",
      "deploy/compose.yml",
      ...args,
    ],
    { cwd: root, stdio: ["ignore", "pipe", "pipe"], encoding: "utf8" },
  );
try {
  compose("stop");
} finally {
  compose("up", "-d", "--wait", "--wait-timeout", "240");
}
// Restart invalidates Node fetch's pooled connections. Retry only transport errors
// while obtaining a session and reading; never retry order mutations here.
let after;
for (let i = 0; i < 5; i++) {
  try {
    after = await data(
      "GET",
      `/admin/orders/${fixture.orderId}`,
      undefined,
      await admin(),
    );
    break;
  } catch (error) {
    if (
      !(error instanceof TypeError && error.message === "fetch failed") ||
      i === 4
    )
      throw error;
    await sleep(500);
  }
}
assert(
  after.status === before.status &&
    after.orderNo === before.orderNo &&
    JSON.stringify(after.items) === JSON.stringify(before.items),
  "容器重启后业务数据改变",
);
report("restart-drill", {
  passed: true,
  persistedOrderId: after.id,
  status: after.status,
  orderAndItemsPersisted: true,
});
