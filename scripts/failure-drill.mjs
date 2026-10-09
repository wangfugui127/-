import { execFileSync } from "node:child_process";
import {
  data,
  user,
  key,
  receiver,
  waitOrder,
  assert,
  report,
  sleep,
  root,
} from "./lib.mjs";
const docker = (...args) =>
  execFileSync("docker", args, {
    cwd: root,
    stdio: ["ignore", "pipe", "pipe"],
    encoding: "utf8",
  });
const project = process.env.COMPOSE_PROJECT ?? "minimall-docker";
const compose = (...args) =>
  docker(
    "compose",
    "-p",
    project,
    "--env-file",
    "deploy/.env",
    "-f",
    "deploy/compose.yml",
    ...args,
  );
const token = await user("drill");
const p = (await data("GET", "/products?size=20")).items[0];
let order;
try {
  compose("stop", "broker");
  const body = {
    source: "DIRECT",
    items: [{ productId: p.id, quantity: 1 }],
    expectedTotalFen: p.priceFen,
    receiver,
  };
  order = await data("POST", "/orders", body, token, key());
  await data(
    "POST",
    `/orders/${order.id}/mock-payments`,
    { scenario: "SUCCESS" },
    token,
  );
  await sleep(7000);
  assert(
    (await data("GET", `/orders/${order.id}`, undefined, token)).status ===
      "PENDING_PAYMENT",
    "Broker停止后意外支付成功",
  );
} finally {
  compose("start", "broker");
}
await waitOrder(order.id, token, "PAID", 150);
// Only expire the newly created test order, never alter unrelated records.
const second = await data(
  "POST",
  "/orders",
  {
    source: "DIRECT",
    items: [{ productId: p.id, quantity: 1 }],
    expectedTotalFen: p.priceFen,
    receiver,
  },
  token,
  key(),
);
assert(/^\d+$/.test(second.id), "invalid test id");
docker(
  "exec",
  `${project}-mysql-1`,
  "sh",
  "-c",
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -e "$1"',
  "--",
  `UPDATE mall_order SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=${second.id} AND status='PENDING_PAYMENT'`,
);
await waitOrder(second.id, token, "CLOSED", 70);
report("failure-drill", {
  passed: true,
  brokerRecoveredOrderId: order.id,
  scannerClosedOrderId: second.id,
  brokerFailureRecovered: true,
  databaseScannerFallback: true,
});
