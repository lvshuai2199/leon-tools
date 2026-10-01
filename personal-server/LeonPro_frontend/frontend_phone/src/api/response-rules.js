/**
 * 响应判定规则（纯函数，node --test 直接测；request.ts 使用）。和管理端一致：
 * - 响应体 status（或 code）不是 200 一律算错误，提示后端 message
 * - 只有 401（HTTP 或响应体）= 登录失效：清 token、跳 /login
 * - 403 / 404 等不清 token；403 没有 message 时提示「没有权限」，其他没有 message 时提示「请求失败」
 * - 响应体里既没有 status 也没有 code（非统一包装），HTTP 成功就原样返回
 */

export const AUTH_EXPIRED_MESSAGE = "登录已失效，请重新登录";
export const FORBIDDEN_MESSAGE = "没有权限";
export const GENERIC_MESSAGE = "请求失败";

/** 响应体业务码：优先 status，其次 code；没有返回 "" */
export function bodyCode(body) {
  if (!body || typeof body !== "object") return "";
  if (body.status !== undefined && body.status !== null && body.status !== "") return String(body.status);
  if (body.code !== undefined && body.code !== null && body.code !== "") return String(body.code);
  return "";
}

export function bodyMessage(body) {
  if (body && typeof body === "object") {
    if (typeof body.message === "string" && body.message) return body.message;
    if (typeof body.msg === "string" && body.msg) return body.msg;
  }
  return "";
}

/**
 * @param {number} httpStatus
 * @param {unknown} body 解析后的 JSON（解析失败为 null）
 * @param {{ authCheck?: boolean }} [opts] authCheck=false：公开接口 / 退出登录，401 只当普通错误
 * @returns {{ kind: "ok", data: unknown } | { kind: "empty" } | { kind: "authExpired", message: string, code: number, fromServer: boolean }
 *   | { kind: "error", message: string, code: string | number | undefined, fromServer: boolean }}
 */
export function interpretResponse(httpStatus, body, opts = {}) {
  const authCheck = opts.authCheck !== false;
  const code = bodyCode(body);
  const message = bodyMessage(body);
  const fromServer = !!message;

  if (httpStatus === 401 || code === "401") {
    if (authCheck) return { kind: "authExpired", message: message || AUTH_EXPIRED_MESSAGE, code: 401, fromServer };
    return { kind: "error", message: message || AUTH_EXPIRED_MESSAGE, code: 401, fromServer };
  }
  if (httpStatus === 403 || code === "403") {
    return { kind: "error", message: message || FORBIDDEN_MESSAGE, code: 403, fromServer };
  }
  const httpOk = httpStatus >= 200 && httpStatus < 300;
  if (!httpOk) return { kind: "error", message: message || GENERIC_MESSAGE, code: code || httpStatus, fromServer };
  if (body === null || body === undefined) {
    return httpStatus === 204 ? { kind: "empty" } : { kind: "error", message: "响应格式错误", code: httpStatus, fromServer: false };
  }
  if (!code) return { kind: "ok", data: body };
  if (code === "200") return { kind: "ok", data: body.data };
  return { kind: "error", message: message || GENERIC_MESSAGE, code, fromServer };
}
