/** 站点全局配置 */

/** 站点名称（顶栏左侧 & 页面标题） */
export const SITE_NAME = '个人空间'

/** 是否启用本地 Mock（.env.development 默认 true） */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/**
 * 接口 & 资源前缀（开发 /dev-api，生产 /prod-api）。
 * 同时用于：接口请求 `${API_PREFIX}/extern/wallpaper/...`，
 * 以及给后端返回的相对图片地址（/uploads/...）补前缀。
 */
export const API_PREFIX = (import.meta.env.VITE_API_PREFIX || '').replace(/\/+$/, '')

/** 壁纸列表每页数量 */
export const WALLPAPER_PAGE_SIZE = 24 // 后端 size 上限 100

/** 分组数超过该值时，桌面端改为左侧分组列表 */
export const GROUP_SIDEBAR_THRESHOLD = 8
