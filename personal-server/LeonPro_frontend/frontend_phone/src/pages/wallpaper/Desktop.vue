<script setup lang="ts">
/**
 * 壁纸浏览 · 电脑版
 * - ≥1200：左侧 220px 分组栏 + 右侧网格
 * - 768–1199：不显示侧栏，顶部横向分组标签
 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useTemplateRef } from 'vue'
import StateBlock from '@/components/StateBlock.vue'
import GroupTabs from './components/GroupTabs.vue'
import GroupSidebar from './components/GroupSidebar.vue'
import ImageGrid from './components/ImageGrid.vue'
import ViewerDesktop from './components/ViewerDesktop.vue'
import { useWallpaperBrowse } from './useWallpaperBrowse'

const { isDesktop: useSidebar } = useBreakpoint()

const {
  groups,
  groupsLoading,
  groupsError,
  loadGroups,
  activeGroup,
  selectGroup,
  images,
  total,
  loading,
  hasMore,
  initialLoading,
  isEmpty,
  notFound,
  notFoundTitle,
  loadError,
  pageError,
  reachedEnd,
  loadMore,
  retryPage,
  goOtherGroup,
  viewerIndex,
  openViewer,
  closeViewer,
} = useWallpaperBrowse(useTemplateRef<HTMLElement>('sentinelEl'))
</script>

<template>
  <div class="browse" :class="{ 'browse--sidebar': useSidebar }">
    <StateBlock v-if="groupsError" type="error" title="分组加载失败" :desc="groupsError" @retry="loadGroups" />
    <StateBlock v-else-if="groupsLoading" type="loading" desc="正在加载分组…" />
    <StateBlock v-else-if="!groups.length" type="empty" title="暂无公开的壁纸分组" desc="稍后再来看看吧" />

    <template v-else>
      <aside v-if="useSidebar" class="browse__aside">
        <!-- 未知分组时 active 为 undefined：不高亮任何分组 -->
        <GroupSidebar :groups="groups" :active="activeGroup?.groupKey" @select="selectGroup" />
      </aside>

      <section class="browse__main">
        <div v-if="!useSidebar" class="browse__tabs">
          <GroupTabs :groups="groups" :active="activeGroup?.groupKey" @select="selectGroup" />
        </div>

        <!-- 不存在状态：标题行只显示分组名（未知分组则显示 URL 中的 key），隐藏描述与数量 -->
        <header v-if="notFound" class="info">
          <h1 class="info__name">分组不存在</h1>
        </header>
        <header v-else-if="activeGroup" class="info">
          <h1 class="info__name">{{ activeGroup.name }}</h1>
          <p v-if="activeGroup.description" class="info__desc">{{ activeGroup.description }}</p>
          <span class="info__count">{{ activeGroup.imageCount }} 张</span>
        </header>

        <ImageGrid :images="images" :skeleton="initialLoading ? 8 : 0" variant="desktop" @open="openViewer" />

        <StateBlock v-if="isEmpty" type="empty" title="这个分组还没有壁纸" desc="去看看其他分组吧" />

        <!-- 不存在或未公开：灰色提示，无重试 -->
        <StateBlock v-if="notFound" type="notfound" :desc="notFoundTitle">
          <el-button type="primary" @click="goOtherGroup">查看其他分组</el-button>
        </StateBlock>

        <!-- 网络错误 / 5xx：红色错误 + 重试 -->
        <StateBlock
          v-else-if="loadError"
          type="error"
          :compact="images.length > 0"
          :title="images.length ? '加载更多失败' : '图片加载失败'"
          :desc="loadError.message"
          @retry="retryPage"
        />

        <div ref="sentinelEl" class="sentinel" aria-hidden="true" />

        <div v-if="loading && images.length" class="more"><span class="more__spinner" />加载中…</div>
        <p v-if="reachedEnd" class="end">— 已经到底了 · 共 {{ images.length }} 张 —</p>
      </section>
    </template>

    <ViewerDesktop
      v-if="viewerIndex >= 0 && images.length"
      v-model:index="viewerIndex"
      :images="images"
      :has-more="hasMore && !pageError"
      :loading-more="loading"
      :total="total"
      @load-more="loadMore"
      @close="closeViewer"
    />
  </div>
</template>

<style scoped lang="scss">
.browse {
  // 分组标题行高度 & 与网格的间距（侧栏「分组」标签共用，保证对齐）
  --group-head-h: 32px;
  --group-head-gap: #{lp.$space-4};
  padding-top: lp.$space-5;
  padding-bottom: 48px;
}
.browse--sidebar {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: lp.$space-6;
  align-items: start;
}
.browse__main {
  min-width: 0;
}
.browse__tabs {
  position: sticky;
  top: var(--topbar-h);
  z-index: 10;
  margin: calc(-1 * #{lp.$space-2}) calc(-1 * #{lp.$page-padding-desktop}) 0;
  padding: lp.$space-2 lp.$page-padding-desktop;
  background: var(--el-bg-color-page);
}
.info {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: lp.$space-1 lp.$space-3;
  margin: lp.$space-3 0 var(--group-head-gap);
}
.browse--sidebar .info {
  margin-top: 0;
}
.info__name {
  margin: 0;
  font-size: lp.$font-size-extra-large;
  line-height: var(--group-head-h);
  font-weight: lp.$font-weight-semibold;
}
.info__desc {
  margin: 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-regular);
}
.info__count {
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.sentinel {
  height: 1px;
}
.more,
.end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: lp.$space-2;
  margin: lp.$space-5 0 0;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.more__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--el-border-color-lighter);
  border-top-color: var(--el-text-color-secondary);
  border-radius: 50%;
  animation: lp-spin 0.8s linear infinite;
}
</style>
