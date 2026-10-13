<script setup lang="ts">
/** 一条分支相对默认分支的小图。空心点是默认顶端，实心点是这条分支。 */
import { computed } from 'vue'
import { mergeLane } from './text.js'

const props = defineProps<{
  branch: { name: string; merge?: string; ahead?: number; behind?: number }
  defaultBranch: string
}>()

const lane = computed(() => mergeLane(props.branch, props.defaultBranch))
</script>

<template>
  <svg v-if="lane" class="lane" :class="`lane--${lane.kind}`" viewBox="0 0 120 36" aria-hidden="true">
    <line
      v-for="(seg, i) in lane.lines"
      :key="`l${i}`"
      :x1="seg.x1"
      :y1="seg.y1"
      :x2="seg.x2"
      :y2="seg.y2"
      :class="{ 'is-fork': i > 0, 'is-dash': seg.dash }"
    />
    <circle
      v-for="(node, i) in lane.nodes"
      :key="`n${i}`"
      :cx="node.x"
      :cy="node.y"
      r="5"
      :class="node.role === 'base' ? 'is-base' : 'is-head'"
    />
    <text v-for="(mark, i) in lane.labels" :key="`t${i}`" :x="mark.x" :y="mark.y">{{ mark.text }}</text>
  </svg>
</template>

<style scoped lang="scss">
.lane {
  flex: none;
  width: 120px;
  height: 36px;
  overflow: visible;
}
line {
  stroke: var(--el-text-color-secondary);
  stroke-width: 2;
  stroke-linecap: round;
  &.is-fork {
    stroke: var(--el-color-warning);
  }
  &.is-dash {
    stroke-dasharray: 3 3;
  }
}
circle {
  stroke-width: 2;
}
.is-base {
  fill: var(--el-bg-color);
  stroke: var(--el-color-primary);
}
.is-head {
  stroke: var(--el-bg-color);
}
.lane--default .is-head,
.lane--identical .is-head {
  fill: var(--el-color-primary);
}
.lane--merged .is-head {
  fill: var(--el-color-success);
}
.lane--ahead .is-head,
.lane--diverged .is-head,
.lane--unmerged .is-head {
  fill: var(--el-color-warning);
}
text {
  fill: var(--el-text-color-secondary);
  font-size: 11px;
  text-anchor: middle;
}
</style>
