<script setup lang="ts">
/** 快速录入（电脑）：左边来源（粘贴框等），右边识别结果 */
import { computed } from 'vue'
import PageBar from '@/components/PageBar.vue'
import StateBlock from '@/components/StateBlock.vue'
import EntrySource from './EntrySource.vue'
import EntryRowCard from './EntryRowCard.vue'
import { useCrabEntry } from './useCrabEntry'

const e = useCrabEntry()
const { shipDate, tab, rawText, parsing, saving, rows, manual } = e
const backTo = computed(() => ({ path: '/crab', query: { date: shipDate.value } }))
</script>

<template>
  <div class="entry-d">
    <PageBar title="快速录入" :back="backTo" desc="粘贴微信 / 表格里的订货信息，识别后核对再入库" />
    <div class="entry-d__grid">
      <section class="entry-d__panel">
        <h2 class="entry-d__title">录入来源</h2>
        <EntrySource
          v-model:ship-date="shipDate"
          v-model:tab="tab"
          v-model:raw-text="rawText"
          :parsing="parsing"
            :manual="manual"
          @parse="e.parseText"
          @paste="e.onPaste"
            @add-manual="e.pushManual"
        />
      </section>
      <section class="entry-d__panel">
        <div class="entry-d__head">
          <h2 class="entry-d__title">识别结果{{ rows.length ? `（${rows.length} 条）` : '' }}</h2>
          <el-button v-if="rows.length" type="danger" text @click="e.clearAll">清空</el-button>
        </div>
        <StateBlock v-if="!rows.length" type="empty" compact title="还没有识别结果" desc="在左边粘贴文本或手动添加" />
        <template v-else>
          <div class="entry-d__rows">
            <EntryRowCard v-for="(row, i) in rows" :key="row.key" :row="row" :index="i" @remove="e.removeRow(i)" />
          </div>
          <div class="entry-d__foot">
            <span class="entry-d__date">出货日期 {{ shipDate }}</span>
            <el-button type="primary" :loading="saving" @click="e.saveAll">入库 {{ rows.length }} 条</el-button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.entry-d {
  padding-bottom: lp.$space-6;
}
.entry-d__grid {
  display: grid;
  grid-template-columns: minmax(320px, 2fr) minmax(0, 3fr);
  gap: lp.$space-5;
  align-items: start;
}
.entry-d__panel {
  padding: lp.$space-5;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.entry-d__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: lp.$space-3;
}
.entry-d__title {
  margin: 0 0 lp.$space-4;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.entry-d__head .entry-d__title {
  margin: 0;
}
.entry-d__rows {
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  padding: 2px;
  :deep(.erc) {
    box-shadow: none;
    border: 1px solid var(--el-border-color-lighter);
  }
}
.entry-d__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: lp.$space-3;
  margin-top: lp.$space-4;
}
.entry-d__date {
  color: var(--el-text-color-secondary);
}
</style>
