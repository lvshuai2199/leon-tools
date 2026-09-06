import { getUserInfo } from "@/utils/auth.js";
import { showToast } from "@/utils/ui.js";

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

    try {
      const res = await fetch(url, init);
      return await unwrapResponse(res);
    } catch (err) {
      if (!err.handled) {
        showToast("网络异常，请稍后重试");
      }
      throw err;
    }
  },

  get(url, data) {
    return this.request({ url, data, method: "GET" });
  },

  post(url, data) {
    return this.request({ url, data, method: "POST" });
  },
};

function beforeRequest(config) {
  config.header = config.header || {};
  const user = getUserInfo();
  if (user?.id) {
    config.header["X-User-Id"] = String(user.id);
  }
  if (user?.username) {
    config.header["X-Username"] = encodeURIComponent(user.username);
  }
  return config;
}

async function unwrapResponse(response) {
  if (response.status !== 200) {
    showToast("请求失败");
    const err = new Error("HTTP " + response.status);
    err.handled = true;
    throw err;
  }

  const body = await response.json().catch(() => null);
  if (!body || typeof body !== "object" || !("status" in body)) {
    return body;
  }

  if (String(body.status) === "200") {
    return body.data;
  }

  showToast(body.message || "请求失败");
  const err = new Error(body.message || "请求失败");
  err.handled = true;
  throw err;
}

export default http;
