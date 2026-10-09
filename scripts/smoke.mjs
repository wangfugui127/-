import {
  data,
  user,
  admin,
  key,
  receiver,
  waitOrder,
  assert,
  report,
} from "./lib.mjs";
const token = await user(),
  manager = await admin();
const p = (await data("GET", "/products?size=20")).items[0];
const body = {
    source: "DIRECT",
    items: [{ productId: p.id, quantity: 1 }],
    expectedTotalFen: p.priceFen,
    receiver,
  },
  requestKey = key();
const order = await data("POST", "/orders", body, token, requestKey);
const duplicate = await data("POST", "/orders", body, token, requestKey);
assert(order.id === duplicate.id, "重复提交创建了两个订单");
const pay = await data(
  "POST",
  `/orders/${order.id}/mock-payments`,
  { scenario: "SUCCESS" },
  token,
);
const duplicatePay = await data(
  "POST",
  `/orders/${order.id}/mock-payments`,
  { scenario: "SUCCESS" },
  token,
);
assert(pay.id === duplicatePay.id, "重复支付产生了两笔支付记录");
await waitOrder(order.id, token, "PAID");
await data(
  "POST",
  `/admin/orders/${order.id}/shipments`,
  { logisticsCompany: "验收快递", trackingNo: `TEST${Date.now()}` },
  manager,
);
await data("POST", `/orders/${order.id}/confirm`, undefined, token);
await waitOrder(order.id, token, "COMPLETED");
const cancel = await data("POST", "/orders", body, token, key());
await data(
  "POST",
  `/orders/${cancel.id}/cancel`,
  { reason: "验收取消" },
  token,
);
await waitOrder(cancel.id, token, "CANCELLED");
const schema = await (await fetch("http://127.0.0.1:8080/v3/api-docs")).json();
const apiCount = Object.values(schema.paths).reduce(
  (n, v) =>
    n +
    Object.keys(v).filter((x) =>
      ["get", "post", "put", "delete", "patch"].includes(x),
    ).length,
  0,
);
assert(apiCount === 34, `API 数量异常: ${apiCount}`);
report("smoke", {
  passed: true,
  orderId: order.id,
  paymentId: pay.id,
  cancelledOrderId: cancel.id,
  duplicateOrder: true,
  duplicatePayment: true,
  apiCount,
});
