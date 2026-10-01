/**
 * 页面和布局之间的「顶栏」约定：页面用 <PageBar> 声明标题、返回去哪、右侧操作，
 * 手机布局的 44px 顶栏据此显示；电脑布局由 PageBar 自己在内容区顶部画一行标题。
 * 同一时刻只有一个页面生效（按 owner 标记，旧页面卸载晚于新页面挂载时不会把新页面的设置清掉）。
 */
import { reactive } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

export interface PageChromeState {
  owner: symbol | null
  title: string
  /** 返回去哪：不填按浏览历史返回（没有历史回首页） */
  back: RouteLocationRaw | null
  /** false：不显示返回箭头 */
  showBack: boolean
}

export const pageChrome = reactive<PageChromeState>({ owner: null, title: '', back: null, showBack: true })

export function setPageChrome(owner: symbol, patch: Partial<Omit<PageChromeState, 'owner'>>) {
  pageChrome.owner = owner
  if (patch.title !== undefined) pageChrome.title = patch.title
  if (patch.back !== undefined) pageChrome.back = patch.back
  if (patch.showBack !== undefined) pageChrome.showBack = patch.showBack
}

export function clearPageChrome(owner: symbol) {
  if (pageChrome.owner !== owner) return
  pageChrome.owner = null
  pageChrome.title = ''
  pageChrome.back = null
  pageChrome.showBack = true
}
