/** 站点全局配置 */

/** 站点名称（顶栏左侧 & 页面标题） */
export const SITE_NAME = '个人空间'

/**
 * 接口 & 资源前缀（开发 /dev-api，线上 /prod-api）。
 * 同时用于接口请求 `${API_PREFIX}/auth/me`，以及给后端返回的相对图片地址（/uploads/...）补前缀。
 */
export const API_PREFIX = (import.meta.env.VITE_API_PREFIX || '').replace(/\/+$/, '')

/**
 * 示例数据（mock）开关，只在开发模式（vite dev）生效，生产构建一律关闭：
 * - true：所有接口都走示例数据
 * - false / 不填：全部走真实后端
 * - 逗号分隔的接口前缀（如 `/public/wallpaper,/auth`）：只有这些接口走示例数据，其余走后端
 */
const RAW_MOCK = import.meta.env.DEV ? String(import.meta.env.VITE_USE_MOCK ?? '').trim() : ''
const MOCK_ALL = RAW_MOCK === 'true'
const MOCK_PREFIXES =
  MOCK_ALL || RAW_MOCK === '' || RAW_MOCK === 'false'
    ? []
    : RAW_MOCK.split(',')
        .map((s) => s.trim())
        .filter(Boolean)

/** 某个接口路径是否使用示例数据 */
export function useMockFor(path: string): boolean {
  if (!import.meta.env.DEV) return false
  if (MOCK_ALL) return true
  return MOCK_PREFIXES.some((p) => path === p || path.startsWith(p.endsWith('/') ? p : `${p}/`))
}

/** 是否有任何接口在用示例数据（开发时在页面角落提示） */
export const MOCK_ENABLED = import.meta.env.DEV && (MOCK_ALL || MOCK_PREFIXES.length > 0)

/** 壁纸列表每页数量（后端 size 上限 100） */
export const WALLPAPER_PAGE_SIZE = 24
