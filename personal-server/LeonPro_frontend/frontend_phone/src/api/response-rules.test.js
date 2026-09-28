import { test } from "node:test";
import assert from "node:assert/strict";
import { interpretResponse, FORBIDDEN_MESSAGE, GENERIC_MESSAGE, AUTH_EXPIRED_MESSAGE } from "./response-rules.js";

test("body status 200：返回 data", () => {
  assert.deepEqual(interpretResponse(200, { status: 200, message: "Success", data: { a: 1 } }), { kind: "ok", data: { a: 1 } });
});

test("HTTP 200 + body status 404 带 message：按错误提示后端文案，不当登录失效", () => {
  const r = interpretResponse(200, { status: 404, message: "客户不存在", data: null });
  assert.equal(r.kind, "error");
  assert.equal(r.message, "客户不存在");
  assert.equal(r.code, "404");
});

test("body status 403 没有 message：提示「没有权限」，不清 token", () => {
  const r = interpretResponse(200, { status: 403, data: null });
  assert.deepEqual(r, { kind: "error", message: FORBIDDEN_MESSAGE, code: 403, fromServer: false });
});

test("HTTP 403：有 message 显示 message，没有显示「没有权限」；都不是登录失效", () => {
  assert.equal(interpretResponse(403, { status: 403, message: "无螃蟹出货权限" }).message, "无螃蟹出货权限");
  const r = interpretResponse(403, null);
  assert.equal(r.kind, "error");
  assert.equal(r.message, FORBIDDEN_MESSAGE);
});

test("401（HTTP 或 body）= 登录失效；公开接口只当普通错误", () => {
  assert.deepEqual(interpretResponse(401, { status: 401, message: "登录已失效，请重新登录" }), {
    kind: "authExpired", message: "登录已失效，请重新登录", code: 401, fromServer: true,
  });
  assert.equal(interpretResponse(200, { status: 401 }).kind, "authExpired");
  assert.equal(interpretResponse(200, { status: 401 }).message, AUTH_EXPIRED_MESSAGE);
  assert.equal(interpretResponse(401, null, { authCheck: false }).kind, "error");
});

test("body code（没有 status）同样判断；非 200 没有 message 提示「请求失败」", () => {
  assert.deepEqual(interpretResponse(200, { code: 200, data: 5 }), { kind: "ok", data: 5 });
  assert.deepEqual(interpretResponse(200, { code: 500 }), { kind: "error", message: GENERIC_MESSAGE, code: "500", fromServer: false });
  assert.equal(interpretResponse(200, { status: 0, data: 1 }).kind, "error");
  assert.equal(interpretResponse(500, null).message, GENERIC_MESSAGE);
  assert.equal(interpretResponse(502, { status: 500, message: "服务器开小差" }).message, "服务器开小差");
});

test("非统一包装：HTTP 成功原样返回；204 为空", () => {
  assert.deepEqual(interpretResponse(200, [1, 2]), { kind: "ok", data: [1, 2] });
  assert.deepEqual(interpretResponse(204, null), { kind: "empty" });
});
