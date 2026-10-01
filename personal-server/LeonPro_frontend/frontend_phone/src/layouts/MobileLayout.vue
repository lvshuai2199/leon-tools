<script setup lang="ts">
/**
 * 手机布局（<768）：44px 标题栏 + 50px 底部标签栏（首页、壁纸、工具、我的），留 iPhone 安全区。
 * 有底部固定操作条的二级页（路由 meta.hideTabbar）不显示标签栏，操作条贴底并自己留安全区。
 * 「工具」「我的」从底部抽屉弹出；工具列表来自 appMenus（登录后才有）。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { SITE_NAME } from '@/config'
import { useSession } from '@/composables/useSession'
import ToolIcon from '@/components/ToolIcon.vue'
import { pageChrome } from '@/composables/usePageChrome'
import TabIcon from './TabIcon.vue'

const route = useRoute()
const router = useRouter()
const { isLoggedIn, displayName, tools, logout, goLogin } = useSession()

const toolsOpen = ref(false)
const meOpen = ref(false)

/** 标签页根页面不显示返回；页面可用 <PageBar :show-back="false"> 关掉 */
const isTabRoot = computed(() => route.name === 'home' || route.name === 'wallpaper')
const showTabbar = computed(() => !route.meta.hideTabbar)
const showBack = computed(() => !isTabRoot.value && pageChrome.showBack)
const title = computed(() => {
  if (route.name === 'home') return SITE_NAME
  // 404 页顶栏标题留空（页面里已有大标题）
  if (route.name === 'notFound') return pageChrome.title || ''
  return pageChrome.title || route.meta.title || SITE_NAME
})

const activeTab = computed(() => {
  if (toolsOpen.value) return 'tools'
  if (meOpen.value) return 'me'
  return route.meta.tab || ''
})

function back() {
  if (pageChrome.back) router.replace(pageChrome.back)
  else if (window.history.state?.back) router.back()
  else router.replace('/')
}

function openTool(path: string) {
  toolsOpen.value = false
  router.push(path)
}

function toLogin() {
  toolsOpen.value = false
  meOpen.value = false
  goLogin()
}

async function onLogout() {
  // 先收起「我的」抽屉，避免和确认抽屉叠在一起
  meOpen.value = false
  await logout()
}

watch(
  () => route.fullPath,
  () => {
    toolsOpen.value = false
    meOpen.value = false
  },
)
</script>

<template>
  <div class="m-layout" :class="{ 'm-layout--no-tabbar': !showTabbar }">
    <header class="navbar">
      <button v-if="showBack" type="button" class="navbar__back" aria-label="返回" @click="back">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1 class="navbar__title">{{ title }}</h1>
      <!-- 页面右侧操作区（PageBar #actions 传送到这里），每个按钮 44×44 -->
      <div id="lp-navbar-actions" class="navbar__actions" />
    </header>

    <main class="m-main">
      <router-view />
    </main>

    <nav v-if="showTabbar" class="tabbar" aria-label="底部导航">
      <router-link to="/" class="tab" :class="{ 'is-active': activeTab === 'home' }">
        <TabIcon name="home" :active="activeTab === 'home'" /><span>首页</span>
      </router-link>
      <router-link to="/wallpaper" class="tab" :class="{ 'is-active': activeTab === 'wallpaper' }">
        <TabIcon name="wallpaper" :active="activeTab === 'wallpaper'" /><span>壁纸</span>
      </router-link>
      <button type="button" class="tab" :class="{ 'is-active': activeTab === 'tools' }" @click="toolsOpen = true">
        <TabIcon name="tools" :active="activeTab === 'tools'" /><span>工具</span>
      </button>
      <button type="button" class="tab" :class="{ 'is-active': activeTab === 'me' }" @click="meOpen = true">
        <TabIcon name="me" :active="activeTab === 'me'" /><span>我的</span>
      </button>
    </nav>

    <el-drawer v-model="toolsOpen" direction="btt" size="auto" title="工具" append-to-body>
      <template v-if="isLoggedIn">
        <ul v-if="tools.length" class="sheet-list">
          <li v-for="t in tools" :key="t.path">
            <button type="button" class="sheet-item" @click="openTool(t.path)">
              <ToolIcon :icon="t.icon" :size="36" />
              <span class="sheet-item__name">{{ t.name }}</span>
              <el-icon class="sheet-item__arrow"><ArrowRight /></el-icon>
            </button>
          </li>
        </ul>
        <p v-else class="sheet-empty">还没有可用工具，请联系管理员开通</p>
      </template>
      <div v-else class="sheet-login">
        <p class="sheet-empty">登录后可使用螃蟹出货、注册码生成等工具</p>
        <el-button type="primary" size="large" class="block-btn" @click="toLogin">去登录</el-button>
      </div>
    </el-drawer>

    <el-drawer v-model="meOpen" direction="btt" size="auto" title="我的" append-to-body>
      <template v-if="isLoggedIn">
        <div class="me">
          <el-avatar :size="48" class="me__avatar">{{ displayName.slice(0, 1) }}</el-avatar>
          <div class="me__name">{{ displayName }}</div>
        </div>
        <el-button size="large" type="danger" plain class="block-btn" @click="onLogout">退出登录</el-button>
      </template>
      <div v-else class="sheet-login">
        <p class="sheet-empty">还没有登录</p>
        <el-button type="primary" size="large" class="block-btn" @click="toLogin">去登录</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.m-layout {
  @include lp.mobile-vars;
  --topbar-h: calc(#{lp.$mobile-topbar-height} + env(safe-area-inset-top));
  --tabbar-h: calc(#{lp.$mobile-tabbar-height} + env(safe-area-inset-bottom));
  min-height: 100vh;
  min-height: 100dvh;
  padding-top: var(--topbar-h);
  padding-bottom: var(--tabbar-h);
  font-size: lp.$font-size-mobile-body;
  /* 页面底部固定操作栏放在标签栏上方（FixedActionBar 用） */
  --lp-fixed-bottom: var(--tabbar-h);
  --lp-fixed-safe: 0px;
}
/* 没有标签栏：操作条贴底，自己加安全区 */
.m-layout--no-tabbar {
  padding-bottom: env(safe-area-inset-bottom);
  --lp-fixed-bottom: 0px;
  --lp-fixed-safe: env(safe-area-inset-bottom);
}
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--topbar-h);
  padding-top: env(safe-area-inset-top);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.navbar__back {
  position: absolute;
  left: 0;
  bottom: 0;
  width: lp.$component-size-mobile;
  height: lp.$mobile-topbar-height;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;
}
.navbar__actions {
  position: absolute;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  /* 相邻两个 44 可点区之间留 8 */
  gap: lp.$space-2;
  height: lp.$mobile-topbar-height;
}
.navbar__title {
  margin: 0;
  max-width: 56%;
  font-size: lp.$font-size-large;
  font-weight: lp.$font-weight-semibold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  height: var(--tabbar-h);
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}
.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: lp.$mobile-tabbar-height;
  padding: 0;
  border: none;
  background: transparent;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  &.is-active {
    color: var(--el-color-primary);
  }
}
.sheet-list {
  list-style: none;
  /* 行高 56、图标 36：上移 10，让标题到第一个图标正好 16 */
  margin: -10px 0 0;
  padding: 0;
}
.sheet-item {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  width: 100%;
  min-height: 56px;
  padding: lp.$space-2 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  text-align: left;
  font-size: lp.$font-size-medium;
  color: var(--el-text-color-primary);
  cursor: pointer;
}
li:last-child .sheet-item {
  border-bottom: none;
}
.sheet-item__name {
  flex: 1;
}
.sheet-item__arrow {
  color: var(--el-text-color-placeholder);
}
.sheet-empty {
  margin: 0 0 lp.$space-4;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.block-btn {
  width: 100%;
}
.me {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  margin-bottom: lp.$space-5;
}
.me__avatar {
  background: var(--el-color-primary);
  font-size: lp.$font-size-large;
}
.me__name {
  font-size: lp.$font-size-large;
  font-weight: lp.$font-weight-semibold;
}
</style>
