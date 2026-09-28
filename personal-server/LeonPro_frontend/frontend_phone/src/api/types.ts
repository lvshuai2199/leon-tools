/** 登录用户（POST /auth/login 返回带 token；GET /auth/me 返回不带 token） */
export interface UserInfo {
  id: string
  username: string
  nickname?: string
  avatarUrl?: string
  email?: string
  roleId?: string
  roleName?: string
  parentId?: string | null
  token?: string
  /** ROOT 的 menuIds 为 null（表示全部） */
  menuIds?: string[] | null
  /** 以下为 GET /auth/me 才有的字段 */
  root?: boolean
  canLoginWeb?: boolean
  /** 注册码子用户相关（注册码页「子用户」入口） */
  regCode?: RegCodeMeInfo
  [key: string]: unknown
}

/** GET /auth/me 的 regCode 对象 */
export interface RegCodeMeInfo {
  /** 客户在注册码页创建的子用户（最底层，不能再建子用户，隐藏「子用户」入口） */
  isSubUser: boolean
  /** 现在还能新建（有权限、顶层账号、createdCount < maxSubUsers） */
  canCreateSubUsers: boolean
  /** 能进「子用户」管理 */
  canManageSubUsers: boolean
  maxSubUsers: number
  /** 启用中的子用户数（停用的不算） */
  createdCount: number
}

/** 用户端菜单（统一成设计文档 6.4 的格式；后端若返回后台菜单字段，会在 utils/app-menus.js 里转换） */
export interface AppMenu {
  id: string
  /** 完整路径，如 /crab、/crab/new */
  path: string
  name: string
  /** 上级完整路径，顶级为 null */
  parent: string | null
  type: 'dir' | 'page'
  icon: string
  sort: number
  hidden: boolean
  component: string
}

/** 分页结果（后端 Page：records/total/current/pages，size 可能缺省） */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface WallpaperGroup {
  id: string
  name: string
  groupKey: string
  description: string
  sort: number
  imageCount: number
  isPublic?: number
  coverUrl?: string
  coverThumbUrl?: string
}

export interface WallpaperImage {
  id: string
  groupId: string
  title: string
  /** 原图：只在大图查看和「下载原图」用 */
  url: string
  /** 缩略图：格子里用 */
  thumbUrl: string
  width: number
  height: number
  fileSize: number
  sort: number
  enabled: boolean
  createTime: string
}

/** 某个配置的次数 */
export interface RegCodeQuotaItem {
  configId: string
  configName: string
  allocated: number
  used: number
  remaining: number
}

/** 注册码额度 GET /common/regCodeUser/myQuota：次数按配置分别计算；后三个是合计，只为兼容旧页面 */
export interface RegCodeQuota {
  unlimited: boolean
  items: RegCodeQuotaItem[]
  generateLimit: number
  generateUsed: number
  remaining: number
}

/** GET /common/regCode/subUsers */
export interface RegCodeSubUserList {
  createdCount: number
  maxSubUsers: number
  canCreate: boolean
  items: Array<{
    id: string
    username: string
    nickname: string
    /** 0 停用、1 启用 */
    status: number
    createTime: string
    usedTotal: number
    allocatedTotal: number
  }>
}

/** GET/POST /common/regCode/subUsers/{id}/quota */
export interface RegCodeSubUserQuota {
  subUserId: string
  items: RegCodeQuotaItem[]
  /** 停用时会退回的未用次数合计 */
  refundableTotal: number
  /** 创建人自己各配置的剩余 */
  creatorRemaining: RegCodeQuotaItem[]
}

/** 螃蟹出货单（/app/crabShipment/*） */
export interface CrabShipment {
  id: string
  /** 公开分享 id（分享链接 /s/crab/{publicId}） */
  publicId?: string
  sharePath?: string
  shipDate: string
  seqNo?: number | null
  customerName: string
  phone?: string
  address?: string
  /** 规格，如 4两公 */
  spec?: string
  /** 数量（只） */
  quantity?: number | string | null
  /** 0/1（后端也可能给 boolean） */
  paid: number | boolean
  shipped: number | boolean
  trackingNo?: string
  remark?: string
}

/** 公开分享页返回（手机号由后端打码） */
export type CrabShipmentPublic = Omit<CrabShipment, 'id'>
