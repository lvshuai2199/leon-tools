<script setup lang="ts">
/**
 * 电脑布局（≥768）：顶栏（站点名、首页、壁纸、已授权工具、登录/用户菜单）+ 内容区。
 * 768–1199 与 ≥1200 共用；侧栏只在页面内部按断点决定（例如壁纸分组栏）。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { SITE_NAME } from '@/config'
import { useSession } from '@/composables/useSession'

const route = useRoute()
const { isLoggedIn, displayName, tools, logout, goLogin } = useSession()

const navItems = computed(() => [
  { path: '/', label: '首页', active: route.name === 'home' },
  { path: '/wallpaper', label: '壁纸', active: route.name === 'wallpaper' },
  ...(isLoggedIn.value
    ? tools.value.map((t) => ({
        path: t.path,
        label: t.name,
        active: route.meta.menuPath === t.path || route.meta.menuPath?.startsWith(`${t.path}/`) === true,
      }))
    : []),
])

function onCommand(cmd: string) {
  if (cmd === 'logout') logout()
}
</script>

<template>
  <div class="d-layout">
    <header class="topbar">
      <div class="topbar__inner">
        <router-link to="/" class="brand">
          <img src="/favicon.svg" alt="" width="24" height="24" />
          <span>{{ SITE_NAME }}</span>
        </router-link>
        <nav class="nav" aria-label="主导航">
          <router-link v-for="n in navItems" :key="n.path" :to="n.path" class="nav__item" :class="{ 'is-active': n.active }">
            {{ n.label }}
          </router-link>
        </nav>
        <div class="topbar__right">
          <el-dropdown v-if="isLoggedIn" trigger="click" @command="onCommand">
            <span class="user">
              <el-avatar :size="28" class="user__avatar">{{ displayName.slice(0, 1) }}</el-avatar>
              <span class="user__name">{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" class="user__logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-else type="primary" @click="goLogin">登录</el-button>
        </div>
      </div>
    </header>
    <main class="d-main" :class="{ 'd-main--narrow': route.meta.narrow }">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.d-layout {
  --topbar-h: 56px;
  min-height: 100vh;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--topbar-h);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.topbar__inner {
  display: flex;
  align-items: center;
  gap: lp.$space-6;
  height: 100%;
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 lp.$page-padding-desktop;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: lp.$space-2;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.nav {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: lp.$space-1;
  overflow-x: auto;
  scrollbar-width: none;
}
.nav__item {
  flex: none;
  height: 32px;
  line-height: 32px;
  padding: 0 lp.$space-3;
  border-radius: lp.$radius-base;
  color: var(--el-text-color-regular);
  transition: background-color 0.2s, color 0.2s;
  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }
  &.is-active {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    font-weight: lp.$font-weight-medium;
  }
}
.topbar__right {
  flex: none;
  display: flex;
  align-items: center;
}
.user {
  display: inline-flex;
  align-items: center;
  gap: lp.$space-2;
  /* 点击区不小于 44px（打开后是「退出登录」） */
  min-height: 44px;
  padding: 0 lp.$space-2;
  border-radius: lp.$radius-base;
  cursor: pointer;
  &:hover,
  &:focus-visible {
    background: var(--el-fill-color-light);
  }
  color: var(--el-text-color-regular);
  outline: none;
}
.user__avatar {
  background: var(--el-color-primary);
  font-size: lp.$font-size-extra-small;
}
.user__name {
  max-width: 160px;
  /* el-dropdown 把行高设成 1，下划线（t_custA）会被裁掉：给够行高，只在横向截断 */
  line-height: 22px;
  overflow: hidden;
  overflow-x: clip;
  overflow-y: visible;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.d-main {
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 lp.$page-padding-desktop;
}
// 表单类页面（注册码生成等）：居中窄栏
.d-main--narrow {
  max-width: 880px;
  padding-bottom: lp.$space-6;
}
</style>
