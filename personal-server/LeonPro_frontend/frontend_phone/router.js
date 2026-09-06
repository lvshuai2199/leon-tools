import { createRouter, createWebHashHistory } from "vue-router";
import { canEnterApp, getUserInfo } from "@/utils/auth.js";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/pages/login/login" },
    {
      path: "/pages/login/login",
      component: () => import("@/pages/login/login.vue"),
    },
    {
      path: "/pages/workspace/workspace",
      component: () => import("@/pages/workspace/workspace.vue"),
      meta: { auth: true },
    },
    { path: "/:pathMatch(.*)*", redirect: "/pages/login/login" },
  ],
});

router.beforeEach((to) => {
  if (!to.meta.auth) return true;
  const user = getUserInfo();
  if (!canEnterApp(user)) return "/pages/login/login";
  return true;
});

export default router;
