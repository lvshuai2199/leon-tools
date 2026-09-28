<script setup lang="ts">
/** 横向分组标签（手机、768–1199）。active 为 undefined 时不高亮任何分组 */
import { nextTick, ref, watch } from 'vue'
import type { WallpaperGroup } from '@/api/types'

const props = defineProps<{ groups: WallpaperGroup[]; active?: string }>()
defineEmits<{ select: [groupKey: string] }>()

const scroller = ref<HTMLElement>()

/** 切换分组时把选中的标签滚到可见区域 */
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

<style scoped lang="scss">
.tabs__scroller {
  display: flex;
  gap: lp.$space-2;
  overflow-x: auto;
  scrollbar-width: none;
  /* 上下各留 6px 给点击区（外观 32、可点 44）；手机上左右滑到屏幕边 */
  padding: 6px 0;
  @include lp.mobile {
    margin: -6px (-(lp.$page-padding-mobile));
    padding: 6px lp.$page-padding-mobile;
  }
  -webkit-overflow-scrolling: touch;
  &::-webkit-scrollbar {
    display: none;
  }
}
.tab {
  position: relative;
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 lp.$space-3;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.2s, color 0.2s, border-color 0.2s;
  &:hover {
    color: var(--el-color-primary);
  }
  &.is-active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary);
    color: var(--el-color-white);
    .tab__count {
      opacity: 1;
      color: rgba(255, 255, 255, 0.9);
    }
  }
  /* 点击区扩到 44px 高 */
  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: -6px;
    bottom: -6px;
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
.tab__name {
  font-size: lp.$font-size-base;
}
.tab__count {
  font-size: lp.$font-size-extra-small;
  opacity: 0.7;
  font-variant-numeric: tabular-nums;
}
</style>
