import { createApp } from 'vue'
import App from './App.vue'
import router, { isNavigating } from './router'
import { configureRequest } from './api/request'
import { userStore } from './stores/user'
import { showToast } from './utils/ui'
import { MOCK_ENABLED } from './config'
import './styles/global.scss'

// 请求层和登录状态的连接：token 来源 + 401 登录失效处理（一轮失效只进来一次）
configureRequest({
  getToken: () => userStore.getToken(),
  onAuthExpired(message) {
    userStore.clearSession()
    showToast(message, 'warning')
    const cur = router.currentRoute.value
    // 公开页面（首页、壁纸等）只清状态不跳转；路由守卫里触发的 401 由守卫自己跳登录
    if (!isNavigating() && cur.meta.requiresAuth) {
      router.replace({ name: 'login', query: { redirect: cur.fullPath } }).catch(() => {})
    }
  },
})

if (MOCK_ENABLED) {
  console.info('[mock] 开发模式示例数据已开启（VITE_USE_MOCK=%s）', import.meta.env.VITE_USE_MOCK)
}

const app = createApp(App)
app.use(router)
router.isReady().then(() => app.mount('#app'))
