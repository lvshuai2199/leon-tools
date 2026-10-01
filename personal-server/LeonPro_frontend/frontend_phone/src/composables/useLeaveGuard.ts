/**
 * 有没保存的内容时，离开页面前先确认「有未保存的内容，确定离开？」（手机底部确认框，电脑确认弹框）。
 * 顶栏返回、浏览器后退、页面里的链接都走路由守卫；刷新/关闭标签页用浏览器自带的提示。
 * 保存、删除后页面自己跳走时用 leave() 跳过确认。
 */
import { onBeforeUnmount, onMounted } from 'vue'
import { onBeforeRouteLeave, useRouter, type RouteLocationRaw } from 'vue-router'
import { confirmAction } from '@/utils/ui'

export const LEAVE_CONFIRM_TEXT = '有未保存的内容，确定离开？'

export function useLeaveGuard(isDirty: () => boolean) {
  const router = useRouter()
  let bypass = false

  onBeforeRouteLeave(async () => {
    if (bypass || !isDirty()) return true
    return confirmAction(LEAVE_CONFIRM_TEXT, '', { confirmText: '离开', cancelText: '继续编辑', danger: true })
  })

  function onBeforeUnload(e: BeforeUnloadEvent) {
    if (bypass || !isDirty()) return
    e.preventDefault()
    e.returnValue = ''
  }
  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

  /** 不确认直接离开（保存成功、删除后） */
  async function leave(to: RouteLocationRaw) {
    bypass = true
    try {
      await router.replace(to)
    } finally {
      bypass = false
    }
  }

  return { leave }
}
