<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { WallpaperGroup } from '@/api/types'

const props = defineProps<{ groups: WallpaperGroup[]; active?: string }>()
defineEmits<{ select: [groupKey: string] }>()

const scroller = ref<HTMLElement>()

/** 切换分组时，把选中的 tab 滚动到可见区域 */
watch(
  () => props.active,
  async () => {
    await nextTick()
    const el = scroller.value?.querySelector<HTMLElement>('.tab.is-active')
    el?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
  },
  { immediate: true },
)
</script>

<template>
  <nav class="tabs" aria-label="壁纸分组">
    <div ref="scroller" class="tabs__scroller" role="tablist">
      <button
        v-for="g in groups"
        :key="g.groupKey"
        type="button"
        role="tab"
        class="tab"
        :class="{ 'is-active': g.groupKey === active }"
        :aria-selected="g.groupKey === active"
        @click="$emit('select', g.groupKey)"
      >
        <span class="tab__name">{{ g.name }}</span>
        <span class="tab__count">{{ g.imageCount }}</span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
.tabs {
  position: relative;
}
.tabs__scroller {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  padding: 2px 0;
  scroll-padding: 0 var(--page-pad);
  -webkit-overflow-scrolling: touch;
}
.tabs__scroller::-webkit-scrollbar {
  display: none;
}
.tab {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 17px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--text);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s;
}
.tab:hover {
  background: rgba(0, 0, 0, 0.08);
}
.tab.is-active {
  background: var(--text);
  color: #fff;
}
.tab:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.tab__name {
  font-size: 14px;
}
.tab__count {
  font-size: 12px;
  opacity: 0.55;
  font-variant-numeric: tabular-nums;
}
</style>
