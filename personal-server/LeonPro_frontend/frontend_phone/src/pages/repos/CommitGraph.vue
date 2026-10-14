<script setup lang="ts">
/**
 * 仓库提交图。左边是分支线，色标是落在这次提交上的分支名。
 */
import { computed } from 'vue'
import { formatWhen, headline, layoutGraph } from './text.js'

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
      <path
        v-for="(line, i) in lane.lines"
        :key="i"
        :d="line.d"
        :stroke="line.color"
        fill="none"
        stroke-width="2.5"
        stroke-linecap="round"
      />
      <circle
        v-for="row in lane.rows"
        :key="row.sha"
        :cx="row.x"
        :cy="row.y"
        r="4.5"
        fill="#fff"
        :stroke="row.color"
        stroke-width="2.5"
      />
    </svg>
    <div class="graph__rows">
      <button
        v-for="row in lane.rows"
        :key="row.sha"
        type="button"
        class="row"
        :class="{ 'is-on': !!selected && row.branches.includes(selected) }"
        :style="{ height: `${lane.rowHeight}px` }"
        :title="headline(row.message)"
        :aria-label="rowLabel(row)"
        @click="emit('open', row.sha)"
      >
        <span class="msg">
          <span class="subject">
            <b>{{ headline(row.message) }}</b>
            <span v-if="row.branches.length" class="pills">
              <span
                v-for="name in row.branches"
                :key="name"
                class="pill"
                :class="{ 'is-on': name === selected }"
                @click.stop="emit('branch', name)"
              >{{ name }}</span>
            </span>
          </span>
          <span class="meta">
            <span class="sha">{{ row.shortSha }}</span>
            <span v-if="row.author" class="author">{{ row.author }}</span>
            <span v-if="formatWhen(row.date)">{{ formatWhen(row.date) }}</span>
          </span>
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.graph {
  display: flex;
  align-items: flex-start;
  width: 100%;
  max-width: 960px;
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
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  min-height: 32px;
  max-height: 32px;
  padding: 0 lp.$space-2;
  overflow: hidden;
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  &.is-on {
    background: #ddf4ff;
  }
}
.msg {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  flex: 1;
  min-width: 0;
  width: 100%;
}
.subject {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-base;
  line-height: 1.2;
  b {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    font-weight: lp.$font-weight-semibold;
    text-overflow: ellipsis;
    white-space: nowrap;
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
  border-radius: 99px;
  background: #1a7f37;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  &.is-on {
    outline: 2px solid #0969da;
    outline-offset: 1px;
  }
}
.meta {
  display: flex;
  flex: none;
  align-items: center;
  gap: lp.$space-2;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
}
.author {
  max-width: 8em;
  overflow: hidden;
  text-overflow: ellipsis;
}
@include lp.mobile {
  .author {
    display: none;
  }
}
</style>
