import { store } from "@/store";
import { usePermissionStoreHook } from "@/store/modules/permission";
import { useDictStoreHook } from "@/store/modules/dict";

import AuthAPI, { type LoginFormData } from "@/api/auth";
import UserAPI, { type UserInfo } from "@/api/system/user";

import { setAccessToken, clearToken } from "@/utils/auth";

export const useUserStore = defineStore("user", () => {
  const userInfo = useStorage<UserInfo>("userInfo", {} as UserInfo);

  /**
   * 登录（对接 LeonPro_backend /auth/login2）
   *
   * 后端返回 SysUsers 而非 JWT token，
   * 此处以本地标记 token 维持登录态，用户信息存入 localStorage
   */
  function login(LoginFormData: LoginFormData) {
    return new Promise<void>((resolve, reject) => {
      AuthAPI.login(LoginFormData)
        .then((data) => {
          if (!data || !data.username) {
            reject("登录失败，请检查用户名或密码");
            return;
          }
          // LeonPro_backend 未启用 JWT，写入会话标记维持登录态
          setAccessToken(`session-${Date.now()}`);
          userInfo.value = {
            ...data,
            avatar: data.avatarUrl,
            roles: ["ROOT"], // 默认给予全部权限（个人后台）
            perms: ["*"],
          };
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * 获取用户信息
   *
   * LeonPro_backend 的 getMyInfo 目前硬编码返回 "leon"，
   * 登录时已将真实用户信息写入本地，这里直接读取本地并回填
   */
  function getUserInfo() {
    return new Promise<UserInfo>((resolve, reject) => {
      // 本地已存在登录时写入的用户信息，直接使用
      if (userInfo.value && userInfo.value.username) {
        resolve(userInfo.value);
        return;
      }
      // 兜底：调用后端接口获取
      UserAPI.getInfo()
        .then((data) => {
          if (!data) {
            reject("Verification failed, please Login again.");
            return;
          }
          userInfo.value = {
            ...data,
            avatar: data.avatarUrl,
            roles: ["ROOT"],
            perms: ["*"],
          };
          resolve(userInfo.value);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * 登出（前端本地清理会话）
   */
  function logout() {
    return new Promise<void>((resolve) => {
      AuthAPI.logout();
      clearUserData();
      resolve();
    });
  }

  /**
   * 刷新 token
   * LeonPro_backend 未启用 token 刷新机制，直接成功返回
   */
  function refreshToken() {
    return Promise.resolve();
  }

  /**
   * 清理用户数据
   */
  function clearUserData() {
    return new Promise<void>((resolve) => {
      clearToken();
      userInfo.value = {} as UserInfo;
      usePermissionStoreHook().resetRouter();
      useDictStoreHook().clearDictionaryCache();
      resolve();
    });
  }

  return {
    userInfo,
    getUserInfo,
    login,
    logout,
    clearUserData,
    refreshToken,
  };
});

/**
 * 用于在组件外部（如在Pinia Store 中）使用 Pinia 提供的 store 实例。
 * 官方文档解释了如何在组件外部使用 Pinia Store：
 * https://pinia.vuejs.org/core-concepts/outside-component-usage.html#using-a-store-outside-of-a-component
 */
export function useUserStoreHook() {
  return useUserStore(store);
}
