import { createRouter, createWebHistory } from 'vue-router'
import { MODULES } from '@/modules'
import { SITE_NAME } from '@/config'
import HomeView from '@/views/HomeView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const router = createRouter({
  // import.meta.env.BASE_URL === '/portal/'
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    ...MODULES.flatMap((m) => m.routes),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    // 同一页面内仅切换参数（如切换壁纸分组）时不强制滚动，由页面自行处理
    if (to.name && to.name === from.name) return false
    return { top: 0 }
  },
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${SITE_NAME}` : SITE_NAME
})

export default router
