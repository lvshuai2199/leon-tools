/**
 * 断点（与 shared/theme.scss 一致）：
 *  - < 768：手机布局（44px 标题栏 + 50px 底部标签栏）
 *  - 768–1199：电脑布局，不显示侧栏
 *  - ≥ 1200：完整电脑布局（壁纸页左侧分组栏）
 * 全局单例，窗口缩放时实时切换；同时给 <html> 加/去 lp-mobile 类（抽屉等挂到 body 的组件也能拿到手机尺寸变量）。
 */
import { computed, readonly, ref } from 'vue'

export const BREAKPOINT_TABLET = 768
export const BREAKPOINT_DESKTOP = 1200

export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

const width = ref(typeof window !== 'undefined' ? window.innerWidth : BREAKPOINT_DESKTOP)
let started = false

function sync() {
  width.value = window.innerWidth
  document.documentElement.classList.toggle('lp-mobile', width.value < BREAKPOINT_TABLET)
}

function start() {
  if (started || typeof window === 'undefined') return
  started = true
  sync()
  // matchMedia 只在跨断点时触发，比 resize 省；resize 兜底（部分 WebView 不支持 MQL 事件）
  for (const q of [`(min-width: ${BREAKPOINT_TABLET}px)`, `(min-width: ${BREAKPOINT_DESKTOP}px)`]) {
    window.matchMedia(q).addEventListener?.('change', sync)
  }
  window.addEventListener('resize', sync, { passive: true })
}

const bp = computed<Breakpoint>(() =>
  width.value < BREAKPOINT_TABLET ? 'mobile' : width.value < BREAKPOINT_DESKTOP ? 'tablet' : 'desktop',
)
const isMobile = computed(() => bp.value === 'mobile')
const isTablet = computed(() => bp.value === 'tablet')
const isDesktop = computed(() => bp.value === 'desktop')

export function useBreakpoint() {
  start()
  return { width: readonly(width), bp, isMobile, isTablet, isDesktop }
}
