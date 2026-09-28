<script setup lang="ts">
/**
 * 壁纸浏览 · 手机版：标题栏下吸顶的横向分组标签 + 两列缩略图，点开全屏滑动查看
 */
import { useTemplateRef } from 'vue'
import StateBlock from '@/components/StateBlock.vue'
import GroupTabs from './components/GroupTabs.vue'
import ImageGrid from './components/ImageGrid.vue'
import ViewerMobile from './components/ViewerMobile.vue'
import { useWallpaperBrowse } from './useWallpaperBrowse'

const {
  groups,
  groupsLoading,
  groupsError,
  loadGroups,
  routeKey,
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
  <div class="mb">
    <StateBlock v-if="groupsError" type="error" title="分组加载失败" :desc="groupsError" @retry="loadGroups" />
    <StateBlock v-else-if="groupsLoading" type="loading" desc="正在加载分组…" />
    <StateBlock v-else-if="!groups.length" type="empty" title="暂无公开的壁纸分组" desc="稍后再来看看吧" />

    <template v-else>
      <div class="mb__tabs">
        <GroupTabs :groups="groups" :active="activeGroup?.groupKey" @select="selectGroup" />
      </div>

      <div class="mb__body">
        <header v-if="notFound" class="info">
          <h2 class="info__name">{{ activeGroup?.name || routeKey }}</h2>
        </header>
        <header v-else-if="activeGroup" class="info">
          <div class="info__row">
            <h2 class="info__name">{{ activeGroup.name }}</h2>
            <span class="info__count">{{ activeGroup.imageCount }} 张</span>
          </div>
          <p v-if="activeGroup.description" class="info__desc">{{ activeGroup.description }}</p>
        </header>

        <ImageGrid :images="images" :skeleton="initialLoading ? 6 : 0" variant="mobile" @open="openViewer" />

        <StateBlock v-if="isEmpty" type="empty" title="这个分组还没有壁纸" desc="去看看其他分组吧" />

        <StateBlock v-if="notFound" type="notfound" :title="notFoundTitle">
          <el-button type="primary" size="large" @click="goOtherGroup">查看其他分组</el-button>
        </StateBlock>

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
      </div>
    </template>

    <ViewerMobile
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
.mb__tabs {
  position: sticky;
  top: var(--topbar-h);
  z-index: 10;
  padding: lp.$space-2 lp.$page-padding-mobile;
  background: var(--el-bg-color-page);
}
.mb__body {
  padding: 0 lp.$page-padding-mobile lp.$space-5;
}
.info {
  margin: lp.$space-1 0 lp.$space-3;
}
.info__row {
  display: flex;
  align-items: baseline;
  gap: lp.$space-2;
}
.info__name {
  margin: 0;
  font-size: lp.$font-size-large;
  font-weight: lp.$font-weight-semibold;
  line-height: 1.5;
}
.info__count {
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.info__desc {
  margin: 2px 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-regular);
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
  margin: lp.$space-4 0 0;
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
