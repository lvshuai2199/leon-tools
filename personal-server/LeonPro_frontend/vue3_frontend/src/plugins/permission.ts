import type { RouteLocationNormalized, RouteRecordRaw } from "vue-router";
import { ElMessage } from "element-plus";
import NProgress from "@/utils/nprogress";
import { getAccessToken, isLegacySessionToken } from "@/utils/auth";
import router from "@/router";
import { usePermissionStore, useUserStore } from "@/store";
import { isSubAccount, PHONE_LOGIN_PATH, WEB_SUBUSER_LOGIN_BLOCKED } from "@/utils/role";

const PUBLIC_PATHS = new Set(["/login", "/trace", "/tool/trace"]);

function isPublicPath(path: string) {
  if (PUBLIC_PATHS.has(path)) return true;
  return path.startsWith("/crab/share");
}

export function setupPermission() {
  router.beforeEach(async (to) => {
    NProgress.start();

    const userStore = useUserStore();
    let token = getAccessToken();
    const hasUser = !!userStore.userInfo?.username;
    // 旧版本的假 token、只有用户信息没有 token、只有 token 没有用户信息：都清理后重新登录
    if ((token && isLegacySessionToken(token)) || !!token !== hasUser) {
      await userStore.clearUserData();
      token = "";
    }
    let isLogin = !!token;
    if (isLogin && isSubAccount(userStore.userInfo)) {
      await userStore.clearUserData();
      isLogin = false;
      if (to.path !== "/login" && !isPublicPath(to.path)) {
        ElMessage.warning(WEB_SUBUSER_LOGIN_BLOCKED);
        NProgress.done();
        window.location.replace(PHONE_LOGIN_PATH);
        return false;
      }
    }
    if (to.path === "/login") {
      if (isLogin) {
        return { path: "/" };
      }
      return true;
    }

    // 轨迹分析为独立公开页：不走菜单权限，未登录也可访问
    if (isPublicPath(to.path)) {
      return true;
    }

    if (isLogin) {
      const permissionStore = usePermissionStore();
      if (permissionStore.isRoutesLoaded) {
        if (to.matched.length === 0) {
          return "/404";
        }
        const title = (to.params.title as string) || (to.query.title as string);
        if (title) {
          to.meta.title = title;
        }
        return true;
      }

      try {
        const dynamicRoutes = await permissionStore.generateRoutes();
        dynamicRoutes.forEach((route: RouteRecordRaw) => {
          try {
            router.addRoute(route);
          } catch (routeError) {
            console.error("跳过无效路由", route.path, routeError);
          }
        });
        return { ...to, replace: true };
      } catch (error) {
        console.error(error);
        NProgress.done();
        return { path: "/dashboard", replace: true };
      }
    }

    NProgress.done();
    return loginRedirect(to);
  });

  router.afterEach(() => {
    NProgress.done();
  });
}

function loginRedirect(to: RouteLocationNormalized) {
  const params = new URLSearchParams(to.query as Record<string, string>);
  const queryString = params.toString();
  const redirect = queryString ? `${to.path}?${queryString}` : to.path;
  return `/login?redirect=${encodeURIComponent(redirect)}`;
}

/** 判断是否有权限 */
export function hasAuth(value: string | string[], type: "button" | "role" = "button") {
  const { roles, perms } = useUserStore().userInfo;

  if (roles.includes("ROOT")) {
    return true;
  }

  const auths = type === "button" ? perms : roles;
  return typeof value === "string"
    ? auths.includes(value)
    : value.some((perm) => auths.includes(perm));
}
