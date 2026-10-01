<script setup lang="ts">
/**
 * 首页（公开）
 * - 未登录：只显示壁纸卡（写死的公开模块）
 * - 登录后：再显示 appMenus 里非 hidden 的工具卡（按 sort）；一个工具都没有时显示状态块「还没有可用工具，请联系管理员开通」
 */
import { onMounted, ref } from 'vue'
import { ArrowRight, Picture } from '@element-plus/icons-vue'
import { randomGroupCover } from '@/api/wallpaper'
import { userStore } from '@/stores/user'
import ToolIcon from '@/components/ToolIcon.vue'
import StateBlock from '@/components/StateBlock.vue'

/** 工具卡说明文字（菜单里没有描述字段，先写在前端） */
const TOOL_DESC: Record<string, string> = {
  '/crab': '录入、查看出货单，导出发货图',
  '/badminton': '场地费、用球费按人数分摊',
  '/regcode': '为客户生成对应注册码',
}

const isLoggedIn = userStore.isLoggedIn
const tools = userStore.tools
/** 菜单刷新过（或本地有缓存）才判断「没有工具」，避免登录后闪一下空状态 */
const menusKnown = () => userStore.state.meLoaded || userStore.state.appMenus.length > 0

const cover = ref<string>()
const coverLoading = ref(true)
const coverLoaded = ref(false)

onMounted(async () => {
  // 登录状态下刷新一次菜单（401 会由请求层清掉登录状态）
  if (isLoggedIn.value) userStore.ensureMe()
  try {
    cover.value = await randomGroupCover()
  } catch {
    cover.value = undefined // 封面失败不影响卡片可用
  } finally {
    coverLoading.value = false
  }
})
</script>

<template>
  <div class="home">
    <section class="home__section">
      <h2 class="home__heading">内容</h2>
      <div class="home__grid">
        <router-link to="/wallpaper" class="wcard">
          <div class="wcard__cover" :class="{ 'lp-skeleton': coverLoading }">
            <img
              v-if="cover"
              :src="cover"
              alt="壁纸"
              class="fade-img"
              :class="{ 'is-loaded': coverLoaded }"
              @load="coverLoaded = true"
              @error="cover = undefined"
            />
            <el-icon v-else-if="!coverLoading" :size="44" class="wcard__placeholder"><Picture /></el-icon>
          </div>
          <div class="wcard__body">
            <h3 class="wcard__name">壁纸</h3>
            <p class="wcard__desc">精选壁纸合集，在线浏览与下载原图</p>
          </div>
        </router-link>
      </div>
    </section>

    <section v-if="isLoggedIn && !tools.length && menusKnown()" class="home__section">
      <h2 class="home__heading">我的工具</h2>
      <div class="home__empty">
        <StateBlock type="empty" icon="grid" compact title="还没有可用工具，请联系管理员开通" />
      </div>
    </section>

    <section v-if="isLoggedIn && tools.length" class="home__section">
      <h2 class="home__heading">我的工具</h2>
      <div class="home__grid home__grid--tools">
        <router-link v-for="t in tools" :key="t.path" :to="t.path" class="tcard">
          <ToolIcon :icon="t.icon" />
          <div class="tcard__body">
            <h3 class="tcard__name">{{ t.name }}</h3>
            <p v-if="TOOL_DESC[t.path]" class="tcard__desc">{{ TOOL_DESC[t.path] }}</p>
          </div>
          <el-icon class="tcard__arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home {
  padding: lp.$space-6 0 48px;
  @include lp.mobile {
    padding: lp.$page-padding-mobile lp.$page-padding-mobile lp.$space-5;
  }
}
.home__section + .home__section {
  margin-top: lp.$space-6;
  @include lp.mobile {
    margin-top: lp.$space-5;
  }
}
.home__heading {
  // 两端统一：主题正文色、16px、600
  margin: 0 0 lp.$space-3;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
  @include lp.mobile {
    margin-bottom: lp.$space-2;
  }
}
.home__grid {
  display: grid;
  gap: lp.$space-5;
  // 电脑（含 768–1199 无侧栏）一行三张
  grid-template-columns: repeat(3, minmax(0, 1fr));
  @include lp.tablet {
    gap: lp.$space-4;
  }
  @include lp.mobile {
    grid-template-columns: 1fr;
    gap: lp.$space-3;
  }
}

/* 没有工具 */
.home__empty {
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}

/* 壁纸卡 */
.wcard {
  display: block;
  border-radius: lp.$radius-card;
  overflow: hidden;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  &:hover {
    transform: translateY(-2px);
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
.wcard__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--el-fill-color);
  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
.wcard__placeholder {
  color: var(--el-text-color-placeholder);
}
.wcard__body {
  padding: lp.$card-padding;
}
.wcard__name,
.tcard__name {
  margin: 0;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.wcard__desc,
.tcard__desc {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 工具卡 */
.tcard {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  min-height: 76px;
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  transition: transform 0.25s ease;
  &:hover {
    transform: translateY(-2px);
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
.tcard__body {
  flex: 1;
  min-width: 0;
}
.tcard__arrow {
  flex: none;
  color: var(--el-text-color-placeholder);
}
</style>
