<script setup lang="ts">
/**
 * 仓库提交图。左边是分支线，色块是落在这次提交上的分支名。
 */
import { computed } from 'vue'
import { headline, layoutGraph } from './text.js'

const props = defineProps<{
  commits: {
    sha: string
    shortSha?: string
    message?: string
    author?: string
    date?: string
    parents?: string[]
    branches?: string[]
  }[]
  selected?: string
}>()

const emit = defineEmits<{
  open: [sha: string]
  branch: [name: string]
}>()

const lane = computed(() => layoutGraph(props.commits))

function rowLabel(row: { branches: string[]; message: string }) {
  const names = row.branches.length ? `${row.branches.join(' ')} ` : ''
  return `${names}${headline(row.message)}`
}
</script>

<template>
  <div class="graph" :style="{ minHeight: `${lane.height}px` }">
    <svg class="graph__lane" :width="lane.width" :height="lane.height" :viewBox="`0 0 ${lane.width} ${lane.height}`" aria-hidden="true">
      <path v-for="(line, i) in lane.lines" :key="i" :d="line.d" :stroke="line.color" fill="none" stroke-width="2" stroke-linecap="round" />
      <circle
        v-for="row in lane.rows"
        :key="row.sha"
        :cx="row.x"
        :cy="row.y"
        r="4.5"
        :fill="row.color"
        stroke="var(--el-bg-color)"
        stroke-width="2"
      />
    </svg>
    <div class="graph__rows">
      <button
        v-for="row in lane.rows"
        :key="row.sha"
        type="button"
        class="row"
        :class="{ 'is-on': row.branches.includes(selected || '') }"
        :style="{ height: `${lane.rowHeight}px` }"
        :aria-label="rowLabel(row)"
        @click="emit('open', row.sha)"
      >
        <span v-if="row.branches.length" class="pills">
          <span
            v-for="name in row.branches"
            :key="name"
            class="pill"
            :class="{ 'is-on': name === selected }"
            :style="{ background: row.color }"
            @click.stop="emit('branch', name)"
          >{{ name }}</span>
        </span>
        <span class="msg">{{ headline(row.message) }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.graph {
  display: flex;
  align-items: flex-start;
  margin: 0 0 lp.$space-3;
}
.graph__lane {
  flex: none;
  display: block;
}
.graph__rows {
  flex: 1;
  min-width: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  &.is-on {
    background: var(--el-color-primary-light-9);
  }
}
.pills {
  display: flex;
  flex: none;
  gap: 4px;
  max-width: 46%;
  overflow: hidden;
}
.pill {
  max-width: 128px;
  overflow: hidden;
  padding: 0 6px;
  border-radius: 4px;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
  &.is-on {
    outline: 2px solid var(--el-text-color-primary);
    outline-offset: 1px;
  }
}
.msg {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-base;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
