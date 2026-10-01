import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from "axios";
import qs from "qs";
import { useUserStoreHook } from "@/store/modules/user";
import { ResultEnum } from "@/enums/ResultEnum";
import { getAccessToken } from "@/utils/auth";
import router from "@/router";

declare module "axios" {
  interface AxiosRequestConfig {
    /**
     * 为 true 时，401 不走「登录失效 → 清理登录态并跳转登录页」流程，按普通错误处理。
     * 设置了 `Authorization: "no-auth"` 的请求（登录、验证码、/public/** 等）会自动置为 true。
     */
    skipAuthRedirect?: boolean;
    /** 为 true 时出错不弹任何提示（如登出这类尽力而为的请求） */
    skipErrorMessage?: boolean;
    /** 内部使用：发请求时携带的 token，用于识别登录态已变化后才返回的过期请求 */
    sentAccessToken?: string;
  }
}

/** 后端约定：token 无效 / 缺失 / 过期时返回 HTTP 401 + { status: 401, message } */
const UNAUTHORIZED = "401";
const FORBIDDEN = "403";
const FORBIDDEN_MESSAGE = "没有权限";
const SESSION_EXPIRED_MESSAGE = "登录已失效，请重新登录";

// 创建 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 50000,
  headers: { "Content-Type": "application/json;charset=utf-8" },
  paramsSerializer: (params) => qs.stringify(params),
});

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Authorization 为 no-auth 时不携带 Token（登录、验证码、公开接口等），且 401 不触发跳转登录
    if (config.headers.Authorization === "no-auth") {
      config.skipAuthRedirect = true;
      delete config.headers.Authorization;
    } else {
      const accessToken = getAccessToken();
      if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      } else {
        delete config.headers.Authorization;
      }
      config.sentAccessToken = accessToken;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 如果响应是二进制流，则直接返回，用于下载文件、Excel 导出等
    if (response.config.responseType === "blob") {
      return response;
    }

    const res = response.data;

    // 响应体不是标准业务结构（如后端返回 HTML 错误页、空响应等）时，
    // 避免 status.toString() 因 status 为 undefined 而报错
    if (!res || typeof res !== "object" || !("status" in res)) {
      if (!response.config.skipErrorMessage) {
        ElMessage.error("服务器返回数据格式异常，请联系管理员");
      }
      return Promise.reject(new Error("Invalid response: 非预期的数据结构"));
    }

    const { status, data, message } = res;

    if (String(status) === ResultEnum.SUCCESS) {
      return data;
    }

    // 业务体 status 为 401（旧接口 HTTP 200 + status 401 的写法也兼容）
    if (isSessionExpiredStatus(status) && !response.config.skipAuthRedirect) {
      return rejectSessionExpired(response.config, message);
    }

    if (!response.config.skipErrorMessage) {
      ElMessage.error(
        message || (String(status) === FORBIDDEN ? FORBIDDEN_MESSAGE : "系统出错-响应")
      );
    }
    return Promise.reject(new Error(message || "Error"));
  },
  async (error) => {
    // 非 2xx 状态码处理 401、403、500 等
    const { config, response } = error;
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    const body = response?.data;
    const hasBody = !!body && typeof body === "object" && "status" in body;
    const bodyStatus = hasBody ? body.status : undefined;
    const message: string | undefined = hasBody ? body.message : undefined;

    if (
      response &&
      (response.status === 401 || isSessionExpiredStatus(bodyStatus)) &&
      !config?.skipAuthRedirect
    ) {
      return rejectSessionExpired(config, message);
    }

    // 403：登录有效但没有权限，只提示，不清登录态
    const isForbidden = response?.status === 403 || String(bodyStatus) === FORBIDDEN;
    if (!config?.skipErrorMessage) {
      if (hasBody || isForbidden) {
        ElMessage.error(message || (isForbidden ? FORBIDDEN_MESSAGE : "系统出错-请求"));
      } else {
        // 网络错误 / 后端未返回业务结构
        ElMessage.error(error?.message || "网络请求失败，请稍后重试");
      }
    }
    return Promise.reject(error.message);
  }
);

export default service;

/**
 * 401 以及模板遗留的 A0230 / A0231（token 失效 / 刷新 token 失效）一律视为登录失效。
 * 后端没有刷新 token 接口，不再尝试刷新，直接重新登录，避免卡在失效状态或循环刷新。
 */
function isSessionExpiredStatus(status: unknown) {
  if (status == null) return false;
  const s = String(status);
  return (
    s === UNAUTHORIZED ||
    s === ResultEnum.ACCESS_TOKEN_INVALID ||
    s === ResultEnum.REFRESH_TOKEN_INVALID
  );
}

/** 正在处理登录失效（提示 + 清理 + 跳转）时为 true，保证并发请求只提示、跳转一次 */
let sessionExpiredHandling = false;

function rejectSessionExpired(config: InternalAxiosRequestConfig | undefined, message?: string) {
  const msg = message || SESSION_EXPIRED_MESSAGE;
  // 请求发出后登录态已经变了（已被清理或已重新登录），这是过期请求，静默失败即可，
  // 避免同一批并发请求在跳转完成后又重复提示 / 把新登录的用户踢下线
  const stale = (config?.sentAccessToken ?? "") !== getAccessToken();
  if (!stale) {
    handleSessionExpired(msg);
  }
  return Promise.reject(new Error(msg));
}

function handleSessionExpired(message: string) {
  if (sessionExpiredHandling) return;
  sessionExpiredHandling = true;

  const current = router.currentRoute.value;
  const onLoginPage = current.path === "/login";
  if (!onLoginPage) {
    ElMessage.warning({ message, grouping: true });
  }

  useUserStoreHook()
    .clearUserData()
    .then(() => {
      if (onLoginPage) return;
      const redirect = current.fullPath;
      return router.replace({
        path: "/login",
        query: redirect && redirect !== "/" ? { redirect } : undefined,
      });
    })
    .catch((e) => console.error("跳转登录页失败", e))
    .finally(() => {
      sessionExpiredHandling = false;
    });
}
