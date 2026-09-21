import { createRouter, createWebHashHistory } from "vue-router";
import { canEnterApp, getUserInfo, homePath } from "@/utils/auth.js";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/pages/login/login" },
    {
      path: "/pages/login/login",
      component: () => import("@/pages/login/login.vue"),
    },
    {
      path: "/pages/home/home",
      component: () => import("@/pages/home/home.vue"),
      meta: { auth: true },
    },
    {
      path: "/pages/workspace/workspace",
      component: () => import("@/pages/workspace/workspace.vue"),
      meta: { auth: true },
    },
    {
      path: "/pages/crab/list",
      component: () => import("@/pages/crab/list.vue"),
      meta: { auth: true },
    },
    {
      path: "/pages/crab/entry",
      component: () => import("@/pages/crab/entry.vue"),
      meta: { auth: true },
    },
    {
      path: "/pages/crab/edit",
      component: () => import("@/pages/crab/edit.vue"),
      meta: { auth: true },
    },
    {
      path: "/pages/crab/share",
      component: () => import("@/pages/crab/share.vue"),
    },
    { path: "/:pathMatch(.*)*", redirect: "/pages/login/login" },
  ],
});

router.beforeEach((to) => {
  if (!to.meta.auth) return true;
  const user = getUserInfo();
  if (!canEnterApp(user)) return "/pages/login/login";
  if (to.path === "/pages/home/home") {
    return homePath(user) === "/pages/home/home" ? true : "/pages/workspace/workspace";
  }
  return true;
});

export default router;
