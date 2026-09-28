/**
 * 用户端唯一的请求层（原手机端 apiUtils/request.js + 原展示端 portal http.ts 合并）。
 *
 * - 接口前缀取环境变量 VITE_API_PREFIX（开发 /dev-api，线上 /prod-api）
 * - 非公开接口带 `Authorization: Bearer <token>`；公开接口（免登录）只剩 /auth/login 和 /public/**
 * - 401（HTTP 状态或响应体 status）= 登录失效：清登录状态、只提示一次、跳 /login
 * - 403 = 只提示「没有权限」，不清 token，不跳登录
 * - 404 / 5xx / 其他错误：优先读后端 JSON 里的 message
 * - 开发模式可按 VITE_USE_MOCK 用示例数据（走同一套解包和 401/403 处理）
 */
import { API_PREFIX, useMockFor } from '@/config'
import { showToast } from '@/utils/ui'

/** 后端统一返回包装 `{ status, message, data }` */
export interface ApiResult<T = unknown> {
  status: number | string
  message?: string
  data: T
}

export class ApiError extends Error {
  /** 业务状态码（响应体里的 status） */
  code?: number | string
  /** HTTP 状态码（网络错误时为 undefined） */
  status?: number
  /** message 是否来自后端响应体 */
  fromServer: boolean
  /** 网络错误（没拿到响应） */
  network: boolean
  /** 已经给用户提示过 */
  handled: boolean
  /** 登录失效（401） */
  authExpired: boolean
  constructor(
    message: string,
    opts: {
      code?: number | string
      status?: number
      fromServer?: boolean
      network?: boolean
      handled?: boolean
      authExpired?: boolean
    } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = opts.code
    this.status = opts.status
    this.fromServer = opts.fromServer ?? false
    this.network = opts.network ?? false
    this.handled = opts.handled ?? false
    this.authExpired = opts.authExpired ?? false
  }
  /** 业务码或 HTTP 码，取数字 */
  get effectiveStatus(): number | undefined {
    const n = Number(this.code)
    if (Number.isFinite(n) && n >= 300) return n
    return this.status
  }
}

export type Query = Record<string, string | number | boolean | undefined | null>

export interface RequestOptions {
  method?: 'GET' | 'POST'
  /** POST 的 JSON 请求体 */
  data?: unknown
  /** 追加到 URL 上的查询参数（空值会跳过） */
  query?: Query
  /** 不弹任何提示（调用方自己展示错误）；401 仍会清登录状态 */
  silent?: boolean
  /** 不做 401 登录失效处理（例如退出登录接口） */
  skipAuthExpired?: boolean
  /** 超时毫秒，0 表示不限 */
  timeout?: number
  signal?: AbortSignal
}

const AUTH_EXPIRED_MESSAGE = '登录已失效，请重新登录'
const FORBIDDEN_MESSAGE = '没有权限'
const NETWORK_MESSAGE = '网络连接失败，请稍后重试'

/** 免登录接口：不带 token，401 也不当作登录失效（与后端白名单一致，/uploads/** 只是静态资源） */
const PUBLIC_PATH_RE = /^\/(?:auth\/login(?:[/?#]|$)|public(?:[/?#]|$)|uploads(?:[/?#]|$))/

/** 视为成功的业务码 */
const SUCCESS_CODES = new Set(['200', '0'])

export function isPublicPath(path: string): boolean {
  return PUBLIC_PATH_RE.test(String(path || ''))
}

/* ------------------------- 与登录状态的连接（main.ts 里配置） ------------------------- */

interface RequestHooks {
  /** 当前 token */
  getToken: () => string | undefined
  /** 登录失效：清登录状态并决定是否跳登录页（只会在一轮失效里调用一次） */
  onAuthExpired: (message: string, opts: { silent: boolean }) => void
}

let hooks: RequestHooks = {
  getToken: () => undefined,
  onAuthExpired: () => {},
}

export function configureRequest(h: Partial<RequestHooks>) {
  hooks = { ...hooks, ...h }
}

// 多个并发请求同时 401 时只提示/跳转一次；登录成功后重置
let authExpiredHandling = false
export function resetAuthExpired() {
  authExpiredHandling = false
}

/* ------------------------------------ 工具 ------------------------------------ */

export function buildQuery(query?: Query): string {
  if (!query) return ''
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
  }
  const s = qs.toString()
  return s ? `?${s}` : ''
}

/**
 * 把后端返回的相对资源地址（如 /uploads/wallpaper/xx.jpg）补上 API_PREFIX：
 *   /uploads/wallpaper/a.jpg -> /prod-api/uploads/wallpaper/a.jpg
 * 已是 http(s):// / 协议相对 // / data: / blob: 或已带前缀的地址原样返回。
 */
export function resolveAssetUrl(u?: string | null): string {
  if (!u) return ''
  if (/^(https?:)?\/\//i.test(u) || /^(data|blob):/i.test(u)) return u
  const path = u.startsWith('/') ? u : `/${u}`
  if (!API_PREFIX || path === API_PREFIX || path.startsWith(`${API_PREFIX}/`)) return path
  return `${API_PREFIX}${path}`
}

export function isNotFound(e: unknown): e is ApiError {
  return e instanceof ApiError && e.effectiveStatus === 404
}

/** 网络错误或 5xx：可以重试的错误 */
export function isRetryable(e: unknown): boolean {
  if (!(e instanceof ApiError)) return true
  if (e.network) return true
  const s = e.effectiveStatus
  return typeof s === 'number' && s >= 500
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error && e.message) return e.message
  return '加载失败，请稍后重试'
}

function bodyMessage(body: unknown): string {
  if (body && typeof body === 'object' && typeof (body as ApiResult).message === 'string') {
    return (body as ApiResult).message || ''
  }
  return ''
}

function bodyStatus(body: unknown): string {
  return body && typeof body === 'object' && 'status' in body ? String((body as ApiResult).status) : ''
}

/* ------------------------------------ 主流程 ------------------------------------ */

async function doFetch(path: string, url: string, init: RequestInit): Promise<Response> {
  if (import.meta.env.DEV && useMockFor(path)) {
    const { mockFetch } = await import('@/mock')
    return mockFetch(path, init)
  }
  return fetch(url, init)
}

export async function request<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method || 'GET'
  const silent = !!opts.silent
  const fullPath = `${path}${buildQuery(opts.query)}`
  const headers: Record<string, string> = { Accept: 'application/json' }
  const init: RequestInit = { method, headers, credentials: 'omit' }

  if (!isPublicPath(path)) {
    const token = hooks.getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }
  if (method === 'POST') {
    headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(opts.data ?? {})
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined
  if ((opts.timeout && opts.timeout > 0) || opts.signal) {
    controller = new AbortController()
    init.signal = controller.signal
    if (opts.signal) {
      if (opts.signal.aborted) controller.abort()
      else opts.signal.addEventListener('abort', () => controller!.abort(), { once: true })
    }
    if (opts.timeout && opts.timeout > 0) timer = setTimeout(() => controller!.abort(), opts.timeout)
  }

  let resp: Response
  try {
    resp = await doFetch(fullPath, `${API_PREFIX}${fullPath}`, init)
  } catch (e) {
    if (timer) clearTimeout(timer)
    if ((e as Error)?.name === 'AbortError' && opts.signal?.aborted) throw e
    const err = new ApiError(NETWORK_MESSAGE, { network: true })
    if (!silent) {
      showToast(err.message)
      err.handled = true
    }
    throw err
  }
  if (timer) clearTimeout(timer)

  const body: unknown = await resp.json().catch(() => null)
  const status = bodyStatus(body)
  const message = bodyMessage(body)

  // 401：登录失效（白名单接口如登录本身返回 401 时只当普通错误）
  if (resp.status === 401 || status === '401') {
    if (!isPublicPath(path) && !opts.skipAuthExpired) {
      const msg = message || AUTH_EXPIRED_MESSAGE
      if (!authExpiredHandling) {
        authExpiredHandling = true
        hooks.onAuthExpired(msg, { silent })
      }
      throw new ApiError(msg, { code: 401, status: resp.status, fromServer: !!message, handled: true, authExpired: true })
    }
    throw fail(message || AUTH_EXPIRED_MESSAGE, { code: 401, status: resp.status, fromServer: !!message }, silent)
  }

  // 403：没有权限。只提示，不清 token、不跳登录
  if (resp.status === 403 || status === '403') {
    throw fail(FORBIDDEN_MESSAGE, { code: 403, status: resp.status, fromServer: !!message }, silent)
  }

  // 其他 HTTP 错误：后端错误响应通常也带统一包装，优先展示 message
  if (!resp.ok) {
    throw fail(message || `服务暂时不可用（HTTP ${resp.status}）`, { code: status || undefined, status: resp.status, fromServer: !!message }, silent)
  }

  if (body === null) {
    // 204 / 空响应
    if (resp.status === 204) return undefined as T
    throw fail('响应格式错误', { status: resp.status }, silent)
  }
  if (!status) return body as T
  if (SUCCESS_CODES.has(status)) return (body as ApiResult<T>).data
  throw fail(message || `请求失败（status: ${status}）`, { code: status, status: resp.status, fromServer: !!message }, silent)
}

function fail(message: string, opts: ConstructorParameters<typeof ApiError>[1], silent: boolean): ApiError {
  const err = new ApiError(message, opts)
  if (!silent) {
    showToast(message)
    err.handled = true
  }
  return err
}

export const http = {
  get<T = unknown>(path: string, query?: Query, opts: Omit<RequestOptions, 'method' | 'query'> = {}) {
    return request<T>(path, { ...opts, method: 'GET', query })
  },
  post<T = unknown>(path: string, data?: unknown, opts: Omit<RequestOptions, 'method' | 'data'> = {}) {
    return request<T>(path, { ...opts, method: 'POST', data })
  },
}

export default request
