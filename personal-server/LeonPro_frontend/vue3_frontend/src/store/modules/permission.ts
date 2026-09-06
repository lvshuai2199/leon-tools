import type { RouteRecordRaw } from "vue-router";
import { constantRoutes } from "@/router";
import { store } from "@/store";
import router from "@/router";

import MenuAPI, { type RouteVO } from "@/api/system/menu";
import { useUserStoreHook } from "@/store/modules/user";
const modules = import.meta.glob("../../views/**/**.vue");
const Layout = () => import("@/layout/index.vue");

export const usePermissionStore = defineStore("permission", () => {
  // 储所有路由，包括静态路由和动态路由
  const routes = ref<RouteRecordRaw[]>([]);
  // 混合模式左侧菜单路由
  const mixedLayoutLeftRoutes = ref<RouteRecordRaw[]>([]);
  // 路由是否加载完成
  const isRoutesLoaded = ref(false);

  /**
   * 获取后台动态路由数据，解析并注册到全局路由
   *
   * @returns Promise<RouteRecordRaw[]> 解析后的动态路由列表
   */
  function generateRoutes() {
    return new Promise<RouteRecordRaw[]>((resolve) => {
      // 携带当前登录用户名，后端按角色返回可访问菜单
      const username = useUserStoreHook().userInfo?.username;
      MenuAPI.getRoutes(username)
        .then((data) => {
          const dynamicRoutes = parseDynamicRoutes(data);
          routes.value = [...constantRoutes, ...dynamicRoutes];
          isRoutesLoaded.value = true;
          resolve(dynamicRoutes);
        })
        .catch((error) => {
          console.error(error);
          // 角色未配菜单或接口失败时仍保持登录，避免被踢回登录页
          routes.value = [...constantRoutes];
          isRoutesLoaded.value = true;
          resolve([]);
        });
    });
  }

  /**
   * 根据父菜单路径设置混合模式左侧菜单
   *
   * @param parentPath 父菜单的路径，用于查找对应的菜单项
   */
  const setMixedLayoutLeftRoutes = (parentPath: string) => {
    const matchedItem = routes.value.find((item) => item.path === parentPath);
    if (matchedItem && matchedItem.children) {
      mixedLayoutLeftRoutes.value = matchedItem.children;
    }
  };

  /**
   * 重置路由
   */
  const resetRouter = () => {
    //  从 router 实例中移除动态路由
    routes.value.forEach((route) => {
      if (route.name && !constantRoutes.find((r) => r.name === route.name)) {
        router.removeRoute(route.name);
      }
    });

    // 清空本地存储的路由和菜单数据
    routes.value = [];
    mixedLayoutLeftRoutes.value = [];
    isRoutesLoaded.value = false;
  };

  return {
    routes,
    mixedLayoutLeftRoutes,
    isRoutesLoaded,
    generateRoutes,
    setMixedLayoutLeftRoutes,
    resetRouter,
  };
});

/**
 * 解析后端返回的路由数据并转换为 Vue Router 兼容的路由配置
 *
 * 顶级路由的 path 必须以 `/` 开头；子路由保持相对路径（如 menu、user）。
 * 在路由管理里把子菜单拖到顶级后，会自动补上 `/`，避免登录时 addRoute 失败。
 */
const parseDynamicRoutes = (rawRoutes: RouteVO[], isRoot = true): RouteRecordRaw[] => {
  const parsedRoutes: RouteRecordRaw[] = [];

  rawRoutes.forEach((route) => {
    const normalizedRoute = { ...route } as RouteRecordRaw;

    if (isRoot) {
      normalizedRoute.path = ensureAbsolutePath(normalizedRoute.path);
    }

    // 处理组件路径
    normalizedRoute.component =
      normalizedRoute.component?.toString() === "Layout"
        ? Layout
        : modules[`../../views/${normalizedRoute.component}.vue`] ||
          modules["../../views/error/404.vue"];

    // 递归解析子路由
    if (normalizedRoute.children) {
      normalizedRoute.children = parseDynamicRoutes(route.children, false);
    }

    parsedRoutes.push(normalizedRoute);
  });

  return parsedRoutes;
};

function ensureAbsolutePath(path: unknown): string {
  const raw = String(path ?? "").trim();
  if (!raw) return "/";
  if (/^https?:\/\//.test(raw)) return raw;
  return raw.startsWith("/") ? raw : `/${raw}`;
}
/**
 * 在组件外使用 Pinia store 实例 @see https://pinia.vuejs.org/core-concepts/outside-component-usage.html
 */
export function usePermissionStoreHook() {
  return usePermissionStore(store);
}
