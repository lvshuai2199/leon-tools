import { store } from "@/store";
import { usePermissionStoreHook } from "@/store/modules/permission";

import AuthAPI, { type LoginFormData } from "@/api/auth";
import { type UserInfo } from "@/api/system/user";

import { getAccessToken, setAccessToken, clearToken } from "@/utils/auth";
import { isSubAccount, resolveLoginRoles, WEB_SUBUSER_LOGIN_BLOCKED } from "@/utils/role";

export const useUserStore = defineStore("user", () => {
  const userInfo = useStorage<UserInfo>("userInfo", {} as UserInfo);

  /**
   * 登录（对接 LeonPro_backend POST /auth/login）
   *
   * 后端返回用户对象，其中 token 为登录凭证（Redis 保存 7 天，有效请求自动续期），
   * 之后所有后台接口以 Authorization: Bearer <token> 鉴权；用户信息存入 localStorage
   */
  function login(LoginFormData: LoginFormData) {
    return new Promise<void>((resolve, reject) => {
      AuthAPI.login(LoginFormData)
        .then((data) => {
          if (!data || !data.username) {
            reject("登录失败，请检查用户名或密码");
            return;
          }
          if (isSubAccount(data)) {
            reject(WEB_SUBUSER_LOGIN_BLOCKED);
            return;
          }
          const token = typeof data.token === "string" ? data.token.trim() : "";
          if (!token) {
            // 不再写入假的会话标记：没有 token 就无法调用后台接口，按登录失败处理
            reject("登录失败：服务端未返回登录凭证（token），请确认后端已升级或联系管理员");
            return;
          }
          setAccessToken(token);
          // token 只放在 access_token 里，password 等敏感字段不写入 userInfo
          const user: Record<string, any> = { ...data };
          delete user.token;
          delete user.password;
          const roles = resolveLoginRoles(data.roleId, data.roleName);
          userInfo.value = {
            ...user,
            avatar: data.avatarUrl,
            roles,
            perms: roles.includes("ROOT") ? ["*"] : [],
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
   * 登录时已将用户信息写入本地，这里优先读本地；
   * 本地没有时调 GET /auth/me（只按 token 取当前用户）
   */
  function getUserInfo() {
    return new Promise<UserInfo>((resolve, reject) => {
      // 本地已存在登录时写入的用户信息，直接使用
      if (userInfo.value && userInfo.value.username) {
        resolve(userInfo.value);
        return;
      }
      // 兜底：调用后端接口获取
      AuthAPI.getMe()
        .then((data) => {
          if (!data) {
            reject("Verification failed, please Login again.");
            return;
          }
          const roles = resolveLoginRoles(data.roleId, data.roleName);
          userInfo.value = {
            ...data,
            avatar: data.avatarUrl,
            roles,
            perms: roles.includes("ROOT") ? ["*"] : [],
          };
          resolve(userInfo.value);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * 登出：尽力调用 POST /auth/logout 让后端作废 token，
   * 不论成功、失败还是 401，都清理本地 token 和用户信息（跳转登录页由调用方负责）
   */
  async function logout() {
    if (getAccessToken()) {
      try {
        await AuthAPI.logout();
      } catch {
        // 尽力而为：网络错误 / token 已失效（401）都不影响本地退出
      }
    }
    await clearUserData();
  }

  /**
   * 清理用户数据
   */
  function clearUserData() {
    return new Promise<void>((resolve) => {
      clearToken();
      userInfo.value = {} as UserInfo;
      usePermissionStoreHook().resetRouter();
      resolve();
    });
  }

  return {
    userInfo,
    getUserInfo,
    login,
    logout,
    clearUserData,
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
