import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";
export const root = fileURLToPath(new URL("../", import.meta.url));
export const env = Object.fromEntries(
  readFileSync(new URL("../deploy/.env", import.meta.url), "utf8")
    .split(/\r?\n/)
    .filter((x) => x.includes("="))
    .map((x) => [x.slice(0, x.indexOf("=")), x.slice(x.indexOf("=") + 1)]),
);
export const base = process.env.BASE_URL ?? "http://127.0.0.1:8080";
export const key = () => randomUUID().replaceAll("-", "");
export async function call(method, path, body, token, requestKey) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (requestKey) headers["Idempotency-Key"] = requestKey;
  const started = performance.now();
  const response = await fetch(`${base}/api/v1${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  const json = await response.json();
  if (!response.ok)
    throw Object.assign(
      new Error(`${response.status}: ${json.code}: ${json.message}`),
      { status: response.status, code: json.code },
    );
  return { ...json, data: json.data, elapsedMs: performance.now() - started };
}
export const data = async (...args) => (await call(...args)).data;
export async function admin() {
  return (
    await data("POST", "/auth/login", {
      username: "admin",
      password: env.BOOTSTRAP_ADMIN_PASSWORD,
    })
  ).token;
}
export async function user(prefix = "smoke") {
  const username = `${prefix}_${Date.now()}_${Math.floor(Math.random() * 10000)}`,
    password = "SmokeTest!2026";
  await data("POST", "/auth/register", { username, password });
  return (await data("POST", "/auth/login", { username, password })).token;
}
export const receiver = {
  name: "验收用户",
  phone: "13800000001",
  address: "演示环境验收地址1号楼",
};
export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export function assert(value, message) {
  if (!value) throw new Error(message);
}
export async function waitOrder(id, token, status, seconds = 60) {
  for (let i = 0; i < seconds; i++) {
    const o = await data("GET", `/orders/${id}`, undefined, token);
    if (o.status === status) return o;
    await sleep(1000);
  }
  throw new Error(`Order ${id} did not become ${status}`);
}
export function report(name, result) {
  mkdirSync(new URL("../artifacts/", import.meta.url), { recursive: true });
  writeFileSync(
    new URL(`../artifacts/${name}.json`, import.meta.url),
    JSON.stringify({ ...result, at: new Date().toISOString(), base }, null, 2),
  );
  console.log(JSON.stringify(result, null, 2));
}
