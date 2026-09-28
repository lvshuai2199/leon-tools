/**
 * 展示模块注册表。
 *
 * 新增模块（例如 notes）只需：
 *   1. 在 MODULES 中追加一项（含 routes）
 *   2. 在 src/views/<module>/ 下编写对应页面
 * 首页卡片与路由都会自动生成。
 */
import type { RouteRecordRaw } from 'vue-router'
import { randomGroupCover } from '@/api/wallpaper'

export interface PortalModule {
  /** 唯一标识 */
  key: string
  /** 模块名称（卡片标题） */
  name: string
  /** 一句话描述 */
  description: string
  /** 图标（emoji 或短文本），封面缺失时作为占位 */
  icon: string
  /** 卡片点击跳转的路由 */
  route: string
  /** 获取卡片封面，返回 undefined 时显示占位 */
  getCover: () => Promise<string | undefined>
  /** 模块自身的路由 */
  routes: RouteRecordRaw[]
}

export const MODULES: PortalModule[] = [
  {
    key: 'wallpaper',
    name: '壁纸',
    description: '精选壁纸合集，在线浏览与下载原图',
    icon: '🖼️',
    route: '/wallpaper',
    // 每次打开首页随机挑选一个非空分组的封面
    getCover: randomGroupCover,
    routes: [
      {
        // 同时匹配 /wallpaper 与 /wallpaper/:groupKey，切换分组时组件不重建
        path: '/wallpaper/:groupKey?',
        name: 'wallpaper',
        component: () => import('@/views/wallpaper/WallpaperBrowse.vue'),
        meta: { title: '壁纸' },
      },
    ],
  },
  // 示例：后续添加笔记模块
  // {
  //   key: 'notes',
  //   name: '笔记',
  //   description: '零散的想法与记录',
  //   icon: '📝',
  //   route: '/notes',
  //   getCover: async () => undefined,
  //   routes: [{ path: '/notes', name: 'notes', component: () => import('@/views/notes/NotesList.vue'), meta: { title: '笔记' } }],
  // },
]
