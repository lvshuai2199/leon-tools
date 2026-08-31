import type { RouteLocationNormalized, RouteRecordRaw } from "vue-router";
import NProgress from "@/utils/nprogress";
import { getAccessToken } from "@/utils/auth";
import router from "@/router";
import { usePermissionStore, useUserStore } from "@/store";

const PUBLIC_PATHS = new Set(["/login", "/trace", "/tool/trace"]);

function isPublicPath(path: string) {
  return PUBLIC_PATHS.has(path);
}

export function setupPermission() {
  router.beforeEach(async (to) => {
    NProgress.start();

    const isLogin = !!getAccessToken();
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
        await useUserStore().clearUserData();
        NProgress.done();
        return loginRedirect(to);
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
