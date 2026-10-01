<script setup lang="ts">
/** 左侧分组栏（≥1200，宽 220px）。active 为 undefined 时不高亮任何分组 */
import type { WallpaperGroup } from '@/api/types'

defineProps<{ groups: WallpaperGroup[]; active?: string }>()
defineEmits<{ select: [groupKey: string] }>()
</script>

<template>
  <nav class="side" aria-label="壁纸分组">
    <p class="side__title">分组</p>
    <ul class="side__list">
      <li v-for="g in groups" :key="g.groupKey">
        <button
          type="button"
          class="side__item"
          :class="{ 'is-active': g.groupKey === active }"
          :aria-current="g.groupKey === active ? 'page' : undefined"
          @click="$emit('select', g.groupKey)"
        >
          <span class="side__name">{{ g.name }}</span>
          <span class="side__count">{{ g.imageCount }}</span>
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped lang="scss">
.side {
  position: sticky;
  top: calc(var(--topbar-h) + #{lp.$space-5});
  max-height: calc(100vh - var(--topbar-h) - #{lp.$space-6});
  overflow-y: auto;
  scrollbar-width: thin;
}
.side__title {
  // 与右侧分组标题行对齐：高度 = 标题行高度，下边距 = 标题行与网格的间距
  height: var(--group-head-h);
  line-height: var(--group-head-h);
  margin: 0 0 var(--group-head-gap);
  padding: 0 lp.$space-3;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.08em;
}
.side__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.side__item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: lp.$space-2;
  height: 40px;
  padding: 0 lp.$space-3;
  border: none;
  border-radius: lp.$radius-base;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: lp.$font-size-base;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s, color 0.2s;
  &:hover {
    background: var(--el-fill-color-light);
  }
  &.is-active {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: lp.$font-weight-semibold;
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }
}
.side__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side__count {
  flex: none;
  font-size: lp.$font-size-extra-small;
  font-weight: lp.$font-weight-regular;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
</style>
