import { clearUserInfo, getUserInfo } from "@/utils/auth.js";
import { showToast } from "@/utils/ui.js";
import router from "@/router.js";

const LOGIN_PATH = "/pages/login/login";
const AUTH_EXPIRED_MESSAGE = "登录已失效，请重新登录";

// 后端白名单：这些接口不需要（也不发送）token
const PUBLIC_PATH_RE = /^\/(?:auth\/(?:login|login2|captcha)(?:[/?#]|$)|(?:extern|public|uploads|wechat\/oa)(?:[/?#]|$))/;
// 登录接口：成功后重置 401 防抖标记
const LOGIN_API_RE = /^\/auth\/(?:login|login2)(?:[?#]|$)/;

// 多个并发请求同时 401 时只提示/跳转一次；下次登录成功后重置
let authExpiredHandling = false;

const http = {
  baseUrl: "/prod-api",

  async request(config) {
    config = beforeRequest(config);
    const url = this.baseUrl + config.url;
    const headers = { ...config.header };
    const init = { method: config.method || "GET", headers };

    if (init.method === "POST") {
      if (!headers["Content-Type"]) headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(config.data ?? {});
    }

    let timer = null;
    if (config.timeout > 0 && typeof AbortController !== "undefined") {
      const controller = new AbortController();
      init.signal = controller.signal;
      timer = setTimeout(() => controller.abort(), config.timeout);
    }

    try {
      const res = await fetch(url, init);
      const data = await unwrapResponse(res, config);
      if (LOGIN_API_RE.test(config.url)) authExpiredHandling = false;
      return data;
    } catch (err) {
      if (!err.handled && !config.silent) {
        showToast("网络异常，请稍后重试");
      }
      throw err;
    } finally {
      if (timer) clearTimeout(timer);
    }
  },

  get(url, data, options) {
    return this.request({ ...options, url, data, method: "GET" });
  },

  post(url, data, options) {
    return this.request({ ...options, url, data, method: "POST" });
  },
};

export function isPublicPath(url) {
  return PUBLIC_PATH_RE.test(String(url || ""));
}

function beforeRequest(config) {
  config.header = config.header || {};
  if (!isPublicPath(config.url)) {
    const token = getUserInfo()?.token;
    if (token) {
      config.header["Authorization"] = `Bearer ${token}`;
    }
  }
  return config;
}

function isAuthExpired(response, body) {
  return response.status === 401 || (body && typeof body === "object" && String(body.status) === "401");
}

function handleAuthExpired(message) {
  if (authExpiredHandling) return;
  authExpiredHandling = true;
  clearUserInfo();
  showToast(message || AUTH_EXPIRED_MESSAGE);
  if (router.currentRoute.value?.path !== LOGIN_PATH) {
    router.replace(LOGIN_PATH).catch(() => {});
  }
}

function fail(message, config) {
  if (!config.silent) showToast(message);
  const err = new Error(message);
  err.handled = true;
  return err;
}

async function unwrapResponse(response, config) {
  const body = await response.json().catch(() => null);

  if (isAuthExpired(response, body)) {
    const message = (body && body.message) || AUTH_EXPIRED_MESSAGE;
    // 白名单接口（如登录）返回 401 时不当作会话失效处理，只提示
    if (!config.silent && !isPublicPath(config.url)) {
      handleAuthExpired(message);
      const err = new Error(message);
      err.handled = true;
      err.authExpired = true;
      throw err;
    }
    throw fail(message, config);
  }

  if (response.status !== 200) {
    throw fail("请求失败", config);
  }

  if (!body || typeof body !== "object" || !("status" in body)) {
    return body;
  }

  if (String(body.status) === "200") {
    return body.data;
  }

  throw fail(body.message || "请求失败", config);
}

export default http;
