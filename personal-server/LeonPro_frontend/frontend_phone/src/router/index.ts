/**
 * 用户端路由（history 模式，部署在站点根路径 /）。
 *
 * ⚠️ 路由不能以 /trace、/cnc、/admin、/uploads、/prod-api 开头：
 *    这些前缀在同一域名下已被其它服务（溯源、CNC、管理端、上传文件、接口代理）占用，
 *    nginx 会先匹配它们，用户端页面永远打不开。menus.json 由 shared/scripts/check-menus.mjs 在 prebuild 检查。
 *
 * - 公开页面（首页、登录、壁纸、蟹单分享、404）写在代码里，不进清单
 * - 需要授权的页面来自 menus.json：先确认登录，再看 GET /auth/me 的 appMenus 里有没有这个路径，没有就回首页并提示
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import menus from './menus.json'
import { userStore } from '@/stores/user'
import { showToast } from '@/utils/ui'
import { SITE_NAME } from '@/config'

declare module 'vue-router' {
  interface RouteMeta {
    /** 公开页面：不需要登录 */
    public?: boolean
    /** 需要登录 + 菜单授权 */
    requiresAuth?: boolean
    /** 清单里的完整路径，用来和 appMenus 比对 */
    menuPath?: string
    title?: string
    /** blank：不套电脑/手机布局（登录页、分享页） */
    layout?: 'blank'
    /** 手机底部标签栏里高亮哪一项 */
    tab?: 'home' | 'wallpaper' | 'tools' | 'me'
    /** 电脑上内容区用居中窄栏（表单类页面） */
    narrow?: boolean
    keepAlive?: boolean
  }
}

interface MenuItem {
  path: string
  name: string
  parent: string | null
  type: string
  component?: string
  hidden?: boolean
  keepAlive?: boolean
  routeName?: string
}

const pages = import.meta.glob('../pages/**/*.vue')

/** 电脑上用居中窄栏的菜单页面 */
const NARROW_PAGES = new Set(['/regcode'])

/** component 指向单个 .vue 或目录（目录里用 index.vue） */
function resolvePage(component: string) {
  const loader = pages[`../pages/${component}.vue`] || pages[`../pages/${component}/index.vue`]
  if (!loader) throw new Error(`[router] 找不到页面组件：${component}`)
  return loader
}

const menuRoutes: RouteRecordRaw[] = (menus as MenuItem[])
  .filter((m) => m.type === 'page' && m.component)
  .map((m) => ({
    path: m.path,
    name: m.routeName || m.path,
    component: resolvePage(m.component!),
    meta: {
      requiresAuth: true,
      menuPath: m.path,
      title: m.name,
      keepAlive: !!m.keepAlive,
      tab: 'tools',
      narrow: NARROW_PAGES.has(m.path),
    },
  }))

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('@/pages/home/index.vue'), meta: { public: true, title: '首页', tab: 'home' } },
  { path: '/login', name: 'login', component: () => import('@/pages/login/index.vue'), meta: { public: true, title: '登录', layout: 'blank' } },
  {
    // 同时匹配 /wallpaper 与 /wallpaper/:groupKey，切换分组时组件不重建；裸 /wallpaper 在页面里跳到第一个公开分组
    path: '/wallpaper/:groupKey?',
    name: 'wallpaper',
    component: () => import('@/pages/wallpaper/index.vue'),
    meta: { public: true, title: '壁纸', tab: 'wallpaper' },
  },
  ...menuRoutes,
  {
    // 蟹单公开分享（后端 sharePath = /s/crab/{publicId}）
    path: '/s/crab/:publicId',
    name: 'crabShare',
    component: () => import('@/pages/crab/share/index.vue'),
    meta: { public: true, title: '出货信息', layout: 'blank' },
  },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/pages/not-found/index.vue'), meta: { public: true, title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    // 同一页面只改 query（例如蟹单换日期）时不回顶部
    if (to.path === from.path) return false
    // 电脑上蟹单列表 ⇄ 详情弹窗（/crab ⇄ /crab/:id）不回顶部
    const crabDialog = (r: typeof to) => r.name === 'crabList' || r.name === 'crabDetail'
    if (window.innerWidth >= 768 && crabDialog(to) && crabDialog(from)) return false
    return { top: 0 }
  },
})

let navigating = false
/** 路由切换进行中（请求层 401 时据此判断要不要自己跳登录，避免和守卫抢跳转） */
export function isNavigating() {
  return navigating
}

router.beforeEach(async (to) => {
  navigating = true
  if (!to.meta.requiresAuth) return true

  if (!userStore.isLoggedIn.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const ok = await userStore.ensureMe()
  if (!ok || !userStore.isLoggedIn.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.menuPath && !userStore.canAccess(to.meta.menuPath)) {
    showToast(`没有「${to.meta.title || to.path}」的使用权限`, 'warning')
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  navigating = false
  const t = to.meta.title
  document.title = t && to.name !== 'home' ? `${t} - ${SITE_NAME}` : SITE_NAME
})
router.onError(() => {
  navigating = false
})

export default router
