import type { RouteLocationNormalized, RouteRecordRaw } from "vue-router";
import NProgress from "@/utils/nprogress";
import { getAccessToken } from "@/utils/auth";
import router from "@/router";
import { usePermissionStore, useUserStore } from "@/store";

export function setupPermission() {
  const whiteList = ["/login"];

  router.beforeEach(async (to) => {
    NProgress.start();

    const isLogin = !!getAccessToken();
    if (isLogin) {
      if (to.path === "/login") {
        return { path: "/" };
      }

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

    if (whiteList.includes(to.path)) {
      return true;
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
