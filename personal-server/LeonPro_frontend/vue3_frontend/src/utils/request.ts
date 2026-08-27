import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from "axios";
import qs from "qs";
import { useUserStoreHook } from "@/store/modules/user";
import { ResultEnum } from "@/enums/ResultEnum";
import { getAccessToken } from "@/utils/auth";
import router from "@/router";

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
    const accessToken = getAccessToken();
    // 如果 Authorization 设置为 no-auth，则不携带 Token，用于登录、刷新 Token 等接口
    if (config.headers.Authorization !== "no-auth" && accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    } else {
      delete config.headers.Authorization;
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

    // 响应体不是标准业务结构（后端返回 HTML 错误页、空响应等）时，
    // 避免 status.toString() 因 status 为 undefined 而崩溃
    if (!res || typeof res !== "object" || !("status" in res)) {
      ElMessage.error("服务器返回数据格式异常，请联系管理员");
      return Promise.reject(new Error("Invalid response: 非预期的数据结构"));
    }

    const { status, data, message } = res;

    if (String(status) === ResultEnum.SUCCESS) {
      return data;
    }

    ElMessage.error(message || "系统出错-拦截器");
    return Promise.reject(new Error(message || "Error"));
  },
  async (error) => {
    // 非 2xx 状态码处理 401、403、500 等
    const { config, response } = error;
    if (response && response.data && typeof response.data === "object" && "status" in response.data) {
      const { status, message } = response.data;
      if (String(status) === ResultEnum.ACCESS_TOKEN_INVALID) {
        // Token 过期，刷新 Token
        return handleTokenRefresh(config);
      } else if (String(status) === ResultEnum.REFRESH_TOKEN_INVALID) {
        return Promise.reject(new Error(message || "Error"));
      } else {
        ElMessage.error(message || "系统出错-报错");
      }
    } else {
      // 网络错误 / 后端未返回业务结构
      ElMessage.error(error?.message || "网络请求失败，请稍后重试");
    }
    return Promise.reject(error.message);
  }
);

export default service;

// 是否正在刷新标识，避免重复刷新
let isRefreshing = false;
// 因 Token 过期导致的请求等待队列
const waitingQueue: Array<() => void> = [];

// 刷新 Token 处理
async function handleTokenRefresh(config: InternalAxiosRequestConfig) {
  return new Promise((resolve) => {
    // 封装需要重试的请求
    const retryRequest = () => {
      config.headers.Authorization = getAccessToken();
      resolve(service(config));
    };

    waitingQueue.push(retryRequest);

    if (!isRefreshing) {
      isRefreshing = true;

      // 刷新 Token
      useUserStoreHook()
        .refreshToken()
        .then(() => {
          // 依次重试队列中所有请求, 重试后清空队列
          waitingQueue.forEach((callback) => callback());
          waitingQueue.length = 0;
        })
        .catch((error: any) => {
          console.log("handleTokenRefresh error", error);
          // 刷新 Token 失败，跳转登录页
          ElNotification({
            title: "提示",
            message: "您的会话已过期，请重新登录",
            type: "info",
          });
          useUserStoreHook()
            .clearUserData()
            .then(() => {
              router.push("/login");
            });
        })
        .finally(() => {
          isRefreshing = false;
        });
    }
  });
}
