/**
 * 登录状态：用户信息、token、用户端菜单 appMenus。
 * 存 localStorage（键 lp_user）；旧手机端的 userInfo 会自动迁移一次，老用户不用重新登录。
 * 准入只看后端返回的 appMenus（和 403），前端不写死角色规则。
 */
import { computed, reactive } from 'vue'
import { fetchMe, logout as apiLogout, type MeRaw } from '@/api/auth'
import { ApiError, resetAuthExpired } from '@/api/request'
import type { AppMenu, UserInfo } from '@/api/types'
import { canAccessMenu, homeToolMenus, menusFromLegacyFlags, normalizeAppMenus } from '@/utils/app-menus.js'
import manifest from '@/router/menus.json'

const STORAGE_KEY = 'lp_user'
const LEGACY_KEYS = ['userInfo', 'userId', 'justLoggedOut']

interface Persisted {
  user: UserInfo | null
  token: string
  appMenus: AppMenu[]
}

function load(): Persisted {
  const empty: Persisted = { user: null, token: '', appMenus: [] }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const p = JSON.parse(raw)
      return {
        user: p?.user && typeof p.user === 'object' ? p.user : null,
        token: typeof p?.token === 'string' ? p.token : '',
        appMenus: Array.isArray(p?.appMenus) ? normalizeAppMenus(p.appMenus) : [],
      }
    }
    // 迁移旧手机端（/h5）的登录信息
    const legacy = localStorage.getItem('userInfo')
    if (legacy) {
      const u = JSON.parse(legacy)
      LEGACY_KEYS.forEach((k) => localStorage.removeItem(k))
      if (u && typeof u === 'object' && typeof u.token === 'string' && u.token) {
        const { token, ...user } = u
        const migrated: Persisted = { user: { ...user, id: String(user.id ?? user.userId ?? '') }, token, appMenus: [] }
        localStorage.setItem(STORAGE_KEY, JSON.stringify(migrated))
        return migrated
      }
    }
  } catch {
    /* 存储损坏或被禁用：当作未登录 */
  }
  return empty
}

const state = reactive({
  ...load(),
  /** 本次打开页面后是否已从 /auth/me 刷新过菜单 */
  meLoaded: false,
})

function persist() {
  try {
    if (!state.token) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, JSON.stringify({ user: state.user, token: state.token, appMenus: state.appMenus }))
  } catch {
    /* ignore */
  }
}

function applyMe(raw: MeRaw) {
  const src = raw?.user && typeof raw.user === 'object' ? { ...raw.user, appMenus: raw.appMenus } : raw
  const { appMenus, token: _token, ...user } = (src || {}) as Record<string, unknown>
  state.user = { ...(state.user || {}), ...(user as UserInfo) }
  let menus = normalizeAppMenus(appMenus as unknown[])
  // 过渡：一期后端 appMenus 固定为空，改用它返回的 canUseCrab / canUseRegCode（见 menusFromLegacyFlags）
  if (!menus.length) menus = menusFromLegacyFlags(user, manifest)
  state.appMenus = menus
  state.meLoaded = true
  persist()
}

let mePromise: Promise<boolean> | null = null

export const userStore = {
  state,

  isLoggedIn: computed(() => !!state.token),
  displayName: computed(() => state.user?.nickname || state.user?.username || '用户'),
  /** GET /auth/me 的 regCode（子用户入口等，第二块用）；没取到时为 null */
  regCode: computed(() => state.user?.regCode ?? null),
  /** 首页 / 工具标签里显示的工具（非 hidden 页面，按 sort） */
  tools: computed(() => homeToolMenus(state.appMenus)),

  getToken(): string | undefined {
    return state.token || undefined
  },

  /** 登录成功：保存用户和 token（登录返回里若顺带有 appMenus 也一起存） */
  setSession(data: UserInfo & { appMenus?: unknown[] }) {
    const { token, appMenus, ...user } = data
    state.user = user as UserInfo
    state.token = String(token || '')
    state.appMenus = Array.isArray(appMenus) ? normalizeAppMenus(appMenus) : []
    state.meLoaded = Array.isArray(appMenus)
    resetAuthExpired()
    persist()
  },

  /** 调 GET /auth/me 刷新用户和 appMenus。401 由请求层统一处理；其他错误抛出 */
  async loadMe(opts: { silent?: boolean } = {}): Promise<boolean> {
    if (!state.token) return false
    const raw = await fetchMe({ silent: opts.silent })
    if (!state.token) return false
    applyMe(raw)
    return true
  },

  /** 本次打开页面后首次需要菜单时刷新一次（并发去重）。网络错误时沿用本地缓存的菜单 */
  async ensureMe(opts: { silent?: boolean } = { silent: true }): Promise<boolean> {
    if (!state.token) return false
    if (state.meLoaded) return true
    if (!mePromise) {
      mePromise = userStore.loadMe(opts).finally(() => {
        mePromise = null
      })
    }
    try {
      return await mePromise
    } catch (e) {
      if (e instanceof ApiError && e.authExpired) return false
      return !!state.token
    }
  },

  /** 清登录状态（401 或退出时） */
  clearSession() {
    state.user = null
    state.token = ''
    state.appMenus = []
    state.meLoaded = false
    persist()
  },

  async logout() {
    await apiLogout()
    userStore.clearSession()
  },

  /** 清单里的菜单路径（如 /crab/:id）是否已授权 */
  canAccess(menuPath: string): boolean {
    return canAccessMenu(state.appMenus, menuPath, manifest)
  },
}

export function useUserStore() {
  return userStore
}
