/** 布局共用：当前用户、工具菜单、退出登录 */
import { useRouter } from 'vue-router'
import { userStore } from '@/stores/user'
import { confirmAction, showToast } from '@/utils/ui'

export function useSession() {
  const router = useRouter()

  async function logout() {
    const ok = await confirmAction('退出登录', '确定退出当前账号？', { confirmText: '退出', danger: true })
    if (!ok) return false
    await userStore.logout()
    showToast('已退出登录', 'success')
    const cur = router.currentRoute.value
    if (cur.meta.requiresAuth) router.replace({ name: 'home' })
    return true
  }

  function goLogin() {
    const cur = router.currentRoute.value
    router.push({ name: 'login', query: cur.name === 'home' || cur.name === 'notFound' ? {} : { redirect: cur.fullPath } })
  }

  return {
    isLoggedIn: userStore.isLoggedIn,
    displayName: userStore.displayName,
    tools: userStore.tools,
    logout,
    goLogin,
  }
}
