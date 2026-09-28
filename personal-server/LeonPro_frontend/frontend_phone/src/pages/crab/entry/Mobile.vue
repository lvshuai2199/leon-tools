<script setup lang="ts">
/** 快速录入（手机）：底部留白 = 按钮 44 + 16 + 安全区（FixedActionBar 处理） */
import { computed } from 'vue'
import PageBar from '@/components/PageBar.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import EntrySource from './EntrySource.vue'
import EntryRowCard from './EntryRowCard.vue'
import { useCrabEntry } from './useCrabEntry'

const e = useCrabEntry()
const { shipDate, tab, rawText, parsing, ocrProgress, saving, rows, manual } = e
const backTo = computed(() => ({ path: '/crab', query: { date: shipDate.value } }))
</script>

<template>
  <div class="entry-m">
    <PageBar title="快速录入" :back="backTo" />

    <section class="entry-m__card">
      <EntrySource
        v-model:ship-date="shipDate"
        v-model:tab="tab"
        v-model:raw-text="rawText"
        :parsing="parsing"
        :ocr-progress="ocrProgress"
        :manual="manual"
        @parse="e.parseText"
        @paste="e.onPaste"
        @photo="e.onPhoto"
        @add-manual="e.pushManual"
      />
    </section>

    <template v-if="rows.length">
      <div class="entry-m__head">
        <span>识别结果 {{ rows.length }} 条，确认后入库</span>
        <el-button type="danger" text class="entry-m__clear" @click="e.clearAll">清空</el-button>
      </div>
      <div class="entry-m__rows">
        <EntryRowCard v-for="(row, i) in rows" :key="row.key" :row="row" :index="i" @remove="e.removeRow(i)" />
      </div>
      <FixedActionBar>
        <el-button type="primary" :loading="saving" @click="e.saveAll">入库 {{ rows.length }} 条（{{ shipDate }}）</el-button>
      </FixedActionBar>
    </template>
  </div>
</template>

<style scoped lang="scss">
.entry-m {
  padding: lp.$page-padding-mobile;
}
.entry-m__card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.entry-m__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: lp.$space-4 0 lp.$space-2;
  color: var(--el-text-color-regular);
}
.entry-m__clear {
  min-height: lp.$component-size-mobile;
}
.entry-m__rows {
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
}
</style>
