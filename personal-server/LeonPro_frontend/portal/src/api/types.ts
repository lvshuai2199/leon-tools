/** 分页结果（后端 Page：records/total/current/pages，size 可能缺省，映射时用请求值补齐） */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface WallpaperGroup {
  /** 32 位字符串 id */
  id: string
  name: string
  groupKey: string
  description: string
  sort: number
  imageCount: number
  /** 是否公开 0/1（portal 只会拿到公开分组，保留类型即可） */
  isPublic?: number
  /** 分组封面原图（可能为空） */
  coverUrl?: string
  /** 分组封面缩略图（首页卡片优先使用） */
  coverThumbUrl?: string
}

export interface WallpaperImage {
  /** 32 位字符串 id */
  id: string
  groupId: string
  title: string
  /** 原图：仅全屏查看器与「下载原图」使用 */
  url: string
  /** 缩略图：网格使用 */
  thumbUrl: string
  width: number
  height: number
  fileSize: number
  sort: number
  enabled: boolean
  createTime: string
}
