/**
 * 壁纸模块 API（只读、公开）。
 *
 * ⚠️ 约定：
 *  - 只使用 /extern/wallpaper/groups 与 /extern/wallpaper/images
 *  - 不调用 /extern/wallpaper/random，不展示/复制任何 token
 *  - 后端字段名若有调整，只需修改本文件中的 mapGroup / mapImage / mapPage
 */
import { USE_MOCK } from '@/config'
import { get, resolveAssetUrl } from './http'
import type { PageResult, WallpaperGroup, WallpaperImage } from './types'

/** Mock 模块按需加载，生产构建（USE_MOCK=false）不会打包进主包 */
const loadMock = () => import('@/mock/wallpaper')

type Raw = Record<string, any>

const num = (v: unknown, d = 0): number => {
  const n = typeof v === 'string' ? Number(v) : (v as number)
  return Number.isFinite(n) ? n : d
}
const str = (v: unknown, d = ''): string => (v === undefined || v === null ? d : String(v))

/* ------------------------- 字段映射（唯一入口） ------------------------- */

export function mapGroup(r: Raw): WallpaperGroup {
  const cover = r.coverUrl ?? r.cover
  const coverThumb = r.coverThumbUrl ?? r.coverThumb
  return {
    id: str(r.id),
    name: str(r.name ?? r.groupName, '未命名分组'),
    groupKey: str(r.groupKey ?? r.key ?? r.id),
    description: str(r.description ?? r.remark),
    sort: num(r.sort),
    imageCount: num(r.imageCount ?? r.count ?? r.total),
    isPublic: r.isPublic === undefined || r.isPublic === null ? undefined : num(r.isPublic),
    coverUrl: cover ? resolveAssetUrl(cover) : undefined,
    coverThumbUrl: coverThumb ? resolveAssetUrl(coverThumb) : undefined,
  }
}

export function mapImage(r: Raw): WallpaperImage {
  return {
    id: str(r.id),
    groupId: str(r.groupId),
    title: str(r.title ?? r.name),
    url: resolveAssetUrl(r.url),
    thumbUrl: resolveAssetUrl(r.thumbUrl),
    width: num(r.width),
    height: num(r.height),
    fileSize: num(r.fileSize ?? r.size),
    sort: num(r.sort),
    enabled: r.enabled === undefined ? true : Boolean(r.enabled),
    createTime: str(r.createTime),
  }
}

/** @param requestedSize 请求时的 size；后端 Page 可能不返回 size，用它补齐 */
export function mapPage<T>(r: Raw | null | undefined, mapItem: (x: Raw) => T, requestedSize = 0): PageResult<T> {
  const src = r ?? {}
  const list: Raw[] = src.records ?? src.list ?? src.rows ?? []
  const size = num(src.size, requestedSize || list.length || 1)
  const total = num(src.total, list.length)
  const current = num(src.current ?? src.pageNum ?? src.page, 1)
  const pages = num(src.pages, size > 0 ? Math.ceil(total / size) : 1)
  return { records: list.map(mapItem), total, size, current, pages }
}

/* ------------------------------- 接口 ------------------------------- */

/** 获取公开分组（按 sort 升序；兜底过滤掉没有图片的分组） */
export async function fetchGroups(signal?: AbortSignal): Promise<WallpaperGroup[]> {
  const raw = USE_MOCK ? await (await loadMock()).groups() : await get<Raw[]>('/extern/wallpaper/groups', undefined, signal)
  return (Array.isArray(raw) ? raw : [])
    .map(mapGroup)
    .filter((g) => g.imageCount > 0)
    .sort((a, b) => a.sort - b.sort)
}

/** 分页获取分组内图片 */
export async function fetchImages(
  groupKey: string,
  current: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResult<WallpaperImage>> {
  const raw = USE_MOCK
    ? await (await loadMock()).images(groupKey, current, size)
    : await get<Raw>('/extern/wallpaper/images', { group: groupKey, current, size }, signal)
  const page = mapPage(raw, mapImage, size)
  page.records = page.records.filter((x) => x.enabled && x.url)
  return page
}

/** 首页卡片封面：随机挑一个非空分组的封面缩略图（coverThumbUrl，缺省回退 coverUrl） */
export async function randomGroupCover(): Promise<string | undefined> {
  const groups = await fetchGroups()
  const covers = groups
    .filter((g) => g.imageCount > 0)
    .map((g) => g.coverThumbUrl || g.coverUrl)
    .filter((u): u is string => !!u)
  if (!covers.length) return undefined
  return covers[Math.floor(Math.random() * covers.length)]
}
