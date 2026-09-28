/**
 * 通用交互：提示、确认、复制。统一走 Element Plus，不再手写 DOM。
 * - 电脑：ElMessageBox 确认框（宽 480）
 * - 手机：底部抽屉确认框（ConfirmDrawer）
 */
import { createVNode, render } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import ConfirmDrawer from '@/components/ConfirmDrawer.vue'
import { BREAKPOINT_TABLET } from '@/composables/useBreakpoint'

type ToastType = 'info' | 'success' | 'warning' | 'error'

export function showToast(message: string, type: ToastType = 'info') {
  ElMessage({ message, type, grouping: true, duration: 2000, offset: 56 })
}

export interface ConfirmOptions {
  confirmText?: string
  cancelText?: string
  /** 危险操作（删除、退出）用红色按钮 */
  danger?: boolean
}

/** 确认框，确定返回 true，取消/关闭返回 false */
export function confirmAction(title: string, content = '', opts: ConfirmOptions = {}): Promise<boolean> {
  const isMobile = typeof window !== 'undefined' && window.innerWidth < BREAKPOINT_TABLET
  if (!isMobile) {
    return ElMessageBox.confirm(content || title, content ? title : '提示', {
      confirmButtonText: opts.confirmText || '确定',
      cancelButtonText: opts.cancelText || '取消',
      confirmButtonType: opts.danger ? 'danger' : 'primary',
      type: opts.danger ? 'warning' : undefined,
      customStyle: { width: 'var(--lp-dialog-width-desktop)', maxWidth: '92vw' },
      autofocus: false,
    })
      .then(() => true)
      .catch(() => false)
  }
  return new Promise((resolve) => {
    const container = document.createElement('div')
    const vnode = createVNode(ConfirmDrawer, {
      title,
      content,
      confirmText: opts.confirmText,
      cancelText: opts.cancelText,
      danger: opts.danger,
      onDone: (ok: boolean) => {
        render(null, container)
        resolve(ok)
      },
    })
    render(vnode, container)
  })
}

export async function copyText(text: string, okMessage = '已复制') {
  const value = String(text)
  try {
    await navigator.clipboard.writeText(value)
    showToast(okMessage, 'success')
    return true
  } catch {
    /* 微信等 WebView 可能没有 clipboard API，退回 execCommand */
  }
  const ta = document.createElement('textarea')
  ta.value = value
  ta.setAttribute('readonly', 'readonly')
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  document.body.appendChild(ta)
  ta.select()
  try {
    const ok = document.execCommand('copy')
    showToast(ok ? okMessage : '复制失败', ok ? 'success' : 'error')
    return ok
  } catch {
    showToast('复制失败', 'error')
    return false
  } finally {
    ta.remove()
  }
}
