/**
 * 极简 HTTP 层：只读、公开接口，永远不携带任何 token / 凭证。
 */
import { API_PREFIX } from '@/config'

/** 后端统一返回包装 `{ status, message, data }`（字段名如有差异，只需改 unwrap） */
export interface ApiResult<T = unknown> {
  status: number | string
  message?: string
  data: T
}

export class ApiError extends Error {
  /** 业务状态码（响应体中的 status） */
  code?: number | string
  /** HTTP 状态码 */
  status?: number
  /** message 是否来自后端响应体（而非前端兜底文案） */
  fromServer: boolean
  constructor(message: string, opts: { code?: number | string; status?: number; fromServer?: boolean } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = opts.code
    this.status = opts.status
    this.fromServer = opts.fromServer ?? false
  }
}

/** 视为成功的业务码 */
const SUCCESS_CODES = new Set<string>(['200', '0'])

/**
 * 解包统一返回结构 `{ status, message, data }`。
 * - status 为 200 或 0 视为成功，返回 data
 * - 否则抛出 ApiError（错误信息取 message）
 * - 若响应本身不是包装结构（没有 status 字段），则原样返回
 */
export function unwrap<T>(body: unknown): T {
  if (body && typeof body === 'object' && 'status' in body) {
    const res = body as ApiResult<T>
    if (SUCCESS_CODES.has(String(res.status))) return res.data
    throw new ApiError(res.message || `请求失败（status: ${res.status}）`, { code: res.status, fromServer: !!res.message })
  }
  return body as T
}

type Query = Record<string, string | number | boolean | undefined | null>

function buildUrl(path: string, query?: Query): string {
  const url = `${API_PREFIX}${path}`
  if (!query) return url
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
  }
  const s = qs.toString()
  return s ? `${url}${url.includes('?') ? '&' : '?'}${s}` : url
}

/** GET 请求并自动解包 */
export async function get<T>(path: string, query?: Query, signal?: AbortSignal): Promise<T> {
  let resp: Response
  try {
    resp = await fetch(buildUrl(path, query), {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      signal,
    })
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') throw e
    throw new ApiError('网络连接失败，请稍后重试')
  }
  if (!resp.ok) {
    // 后端错误响应通常也带统一包装（如 404 {status, message: '分组不存在或未公开'}），优先展示其 message
    const body = await resp.json().catch(() => null)
    if (body && typeof body === 'object' && typeof (body as ApiResult).message === 'string' && (body as ApiResult).message) {
      const res = body as ApiResult
      throw new ApiError(res.message!, { code: res.status, status: resp.status, fromServer: true })
    }
    throw new ApiError(`服务暂时不可用（HTTP ${resp.status}）`, { status: resp.status })
  }
  let body: unknown
  try {
    body = await resp.json()
  } catch {
    throw new ApiError('响应格式错误')
  }
  return unwrap<T>(body)
}

/**
 * 把后端返回的相对图片地址（如 /uploads/wallpaper/xx.jpg）补上 API_PREFIX：
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

/** 是否为“资源不存在”（HTTP 404）错误 */
export function isNotFound(e: unknown): e is ApiError {
  return e instanceof ApiError && e.status === 404
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error && e.message) return e.message
  return '加载失败，请稍后重试'
}
