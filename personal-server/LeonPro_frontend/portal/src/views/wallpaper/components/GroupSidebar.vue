<script setup lang="ts">
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

<style scoped>
.side {
  position: sticky;
  top: calc(var(--topbar-h) + 20px);
  max-height: calc(100vh - var(--topbar-h) - 40px);
  overflow-y: auto;
  scrollbar-width: thin;
}
.side__title {
  /*
   * 与右侧分组标题行对齐：
   * - 高度/行高 = 标题行高度（var(--group-head-h)），「分组」与标题垂直居中在同一行
   * - 下边距 = 标题行与网格的间距（var(--group-head-gap)），首个分组项顶部与网格首行顶部对齐
   */
  height: var(--group-head-h);
  line-height: var(--group-head-h);
  margin: 0 0 var(--group-head-gap);
  padding: 0 12px;
  font-size: 12px;
  color: var(--text-3);
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
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.2s;
}
.side__item:hover {
  background: rgba(0, 0, 0, 0.05);
}
.side__item.is-active {
  background: var(--surface);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  font-weight: 600;
}
.side__item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.side__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side__count {
  flex: none;
  font-size: 12px;
  font-weight: 400;
  color: #8a8f98;
  font-variant-numeric: tabular-nums;
}
</style>
