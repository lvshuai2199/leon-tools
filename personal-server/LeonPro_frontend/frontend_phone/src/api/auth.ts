/** 登录相关：POST /auth/login、POST /auth/logout、GET/POST /auth/me */
import { http } from './request'
import type { UserInfo } from './types'

/** GET /auth/me 原始返回：用户字段 + appMenus（字段格式见 utils/app-menus.js 的兼容说明） */
export type MeRaw = Record<string, unknown> & { appMenus?: unknown[]; user?: Record<string, unknown> }

export function login(params: { username: string; password: string }) {
  return http.post<UserInfo>('/auth/login', {
    username: params.username,
    password: params.password,
    source: 'app',
  })
}

/** 退出登录：尽力通知后端作废 token，失败/超时都忽略（不提示、不触发 401 跳转） */
export function logout() {
  return http.post('/auth/logout', {}, { silent: true, skipAuthExpired: true, timeout: 3000 }).catch(() => null)
}

/** 当前用户 + 用户端菜单 */
export function fetchMe(opts: { silent?: boolean } = {}) {
  return http.get<MeRaw>('/auth/me', undefined, { silent: opts.silent })
}

/** 改自己的资料（password 留空表示不改） */
export function updateMe(data: { nickname?: string; email?: string; password?: string }) {
  return http.post('/auth/me', data)
}
