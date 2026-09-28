<script setup lang="ts">
/** 登录：POST /auth/login（source=app）→ 存用户和 token → GET /auth/me 取 appMenus → 回到来源页或首页 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { userStore } from '@/stores/user'
import { showToast } from '@/utils/ui'
import { SITE_NAME } from '@/config'
import { useBreakpoint } from '@/composables/useBreakpoint'

const route = useRoute()
const router = useRouter()
const { isMobile } = useBreakpoint()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
}

/** 登录后去哪：只接受站内路径，防止跳到外站 */
const redirect = computed(() => {
  const r = route.query.redirect
  const s = typeof r === 'string' ? r : ''
  return s.startsWith('/') && !s.startsWith('//') && !s.startsWith('/login') ? s : '/'
})

onMounted(() => {
  if (userStore.isLoggedIn.value) router.replace(redirect.value)
})

async function onSubmit() {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await login({ username: form.username.trim(), password: form.password })
    if (!data || !data.token) {
      showToast('登录失败，请检查用户名或密码', 'error')
      return
    }
    userStore.setSession(data)
    // 取用户端菜单；失败不影响登录（首页只显示公开卡片，进工具页时守卫会再取一次）
    await userStore.loadMe({ silent: true }).catch(() => false)
    showToast('登录成功', 'success')
    router.replace(redirect.value)
  } catch {
    /* 请求层已提示（如「用户名或密码错误」） */
  } finally {
    loading.value = false
  }
}

function goHome() {
  router.replace('/')
}
</script>

<template>
  <div class="login" :class="{ 'login--mobile': isMobile }">
    <div class="login__card">
      <div class="brand">
        <img src="/favicon.svg" alt="" class="brand__logo" width="48" height="48" />
        <h1 class="brand__title">{{ SITE_NAME }}</h1>
        <p class="brand__sub">登录后使用螃蟹出货、注册码生成等工具</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" native-type="submit" class="login__submit" :loading="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>

      <div class="login__foot">
        <el-button link type="primary" @click="goHome">先随便看看</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: lp.$space-6 lp.$space-4;
  background: var(--el-bg-color-page);
}
.login__card {
  width: 100%;
  max-width: 400px;
  padding: lp.$space-6 lp.$space-6 lp.$space-5;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.login--mobile {
  @include lp.mobile-vars;
  align-items: flex-start;
  padding: calc(48px + env(safe-area-inset-top)) lp.$page-padding-mobile lp.$space-6;
  background: var(--el-bg-color);
  .login__card {
    max-width: none;
    padding: 0 lp.$space-1;
    box-shadow: none;
  }
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: lp.$space-5;
  text-align: center;
}
.brand__logo {
  border-radius: lp.$radius-card;
}
.brand__title {
  margin: lp.$space-3 0 0;
  font-size: lp.$font-size-extra-large;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.brand__sub {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
}
.login__submit {
  width: 100%;
  margin-top: lp.$space-2;
}
.login__foot {
  display: flex;
  justify-content: center;
  margin-top: lp.$space-4;
}
</style>
