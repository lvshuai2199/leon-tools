<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight, Delete, MoreFilled, Picture, Plus, Search } from '@element-plus/icons-vue'
import type { BadmintonBill } from '@/api/types'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import DateField from '@/components/DateField.vue'
import StateBlock from '@/components/StateBlock.vue'
import ActionSheet, { type SheetAction } from '@/components/ActionSheet.vue'
import { formatMoney } from '@/utils/badminton-bill'
import { useBadmintonListPage, type DateMode } from './useBadmintonListPage'

const list = useBadmintonListPage()
const { filter, records, loading, loaded, error, summary } = list

const keyword = ref(filter.keyword)
const modeOptions = [
  { label: '单日', value: 'day' },
  { label: '区间', value: 'range' },
  { label: '全部', value: 'all' },
]
const mode = computed({
  get: () => filter.mode,
  set: (v: DateMode) => list.setMode(v),
})

const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const beforeStart = (d: Date) => !!filter.start && ymd(d) < filter.start
const afterEnd = (d: Date) => !!filter.end && ymd(d) > filter.end

const moreOpen = ref(false)
const moreItem = ref<BadmintonBill | null>(null)
const moreActions: SheetAction[] = [
  { key: 'sheet', label: '结算图', icon: Picture },
  { key: 'delete', label: '删除', icon: Delete, danger: true },
]
function openMore(item: BadmintonBill) {
  moreItem.value = item
  moreOpen.value = true
}
function onMore(key: string) {
  const item = moreItem.value
  if (!item) return
  if (key === 'sheet') list.exportSheet(item)
  if (key === 'delete') list.remove(item)
}
function doSearch() {
  list.search(keyword.value)
}
</script>

<template>
  <div class="bd-m">
    <PageBar title="羽毛球计费" back="/">
      <template #actions>
        <IconAction :icon="Plus" label="新建球局" @click="list.goNew" />
      </template>
    </PageBar>

    <section class="filters">
      <el-segmented v-model="mode" :options="modeOptions" block class="filters__mode" />
      <div v-if="filter.mode === 'day'" class="filters__day">
        <el-button :icon="ArrowLeft" aria-label="前一天" class="filters__shift" @click="list.shift(-1)" />
        <DateField :model-value="filter.date" title="球局日期" @change="list.setDate" />
        <el-button :icon="ArrowRight" aria-label="后一天" class="filters__shift" @click="list.shift(1)" />
      </div>
      <div v-else-if="filter.mode === 'range'" class="filters__range">
        <DateField
          :model-value="filter.start"
          title="选择开始日期"
          placeholder="开始日期"
          aria-label="开始日期"
          :show-week="false"
          :disabled-date="afterEnd"
          @change="(v) => list.setRange(v, filter.end)"
        />
        <span class="filters__to">至</span>
        <DateField
          :model-value="filter.end"
          title="选择结束日期"
          placeholder="结束日期"
          aria-label="结束日期"
          :show-week="false"
          :disabled-date="beforeStart"
          :mark-date="filter.start"
          @change="(v) => list.setRange(filter.start, v)"
        />
      </div>
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜标题"
        clearable
        enterkeyhint="search"
        @keyup.enter="doSearch"
        @clear="doSearch"
      />
    </section>

    <p v-if="records.length" class="summary">
      共 {{ summary.count }} 局<span class="dot">·</span>合计 {{ summary.total }} 元
    </p>

    <StateBlock v-if="loading && !records.length" type="loading" compact />
    <StateBlock v-else-if="error" type="error" title="加载失败" :desc="error" @retry="list.load()" />
    <StateBlock v-else-if="loaded && !records.length" type="empty" icon="list" :title="list.empty.value">
      <el-button type="primary" :icon="Plus" @click="list.goNew">新建球局</el-button>
    </StateBlock>

    <ul v-else class="cards">
      <li v-for="item in records" :key="item.id">
        <article class="card" @click="list.openDetail(item)">
          <div class="card__head">
            <h3 class="card__name">{{ item.title || '未填标题' }}</h3>
            <span class="card__per">{{ formatMoney(item.perPerson) }} / 人</span>
          </div>
          <p class="card__muted">{{ item.playDate || '-' }} · {{ item.participantCount || 0 }}人</p>
          <p class="card__muted">
            场地 {{ formatMoney(item.courtTotal) }} · 用球 {{ formatMoney(item.ballTotal) }} · 总计
            {{ formatMoney(item.grandTotal) }}
          </p>
          <div class="card__actions" @click.stop>
            <el-button type="primary" plain @click="list.openDetail(item)">编辑</el-button>
            <el-button :icon="MoreFilled" aria-label="更多" @click="openMore(item)">更多</el-button>
          </div>
        </article>
      </li>
    </ul>

    <ActionSheet v-model="moreOpen" :title="moreItem ? moreItem.title || '球局' : ''" :actions="moreActions" @select="onMore" />
  </div>
</template>

<style scoped lang="scss">
.bd-m {
  padding: lp.$page-padding-mobile;
}
.filters {
  display: flex;
  flex-direction: column;
  gap: lp.$space-2;
  margin-bottom: lp.$space-3;
}
.filters__day,
.filters__range {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  .el-button + .el-button {
    margin-left: 0;
  }
}
.filters__shift {
  flex: none;
  width: lp.$component-size-mobile;
  padding: 0;
}
.filters__range > :deep(.df-trigger) {
  flex: 1;
  min-width: 0;
}
.filters__to {
  flex: none;
  color: var(--el-text-color-secondary);
}
.summary {
  margin: 0 0 lp.$space-3;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-regular);
  .dot {
    margin: 0 lp.$space-1;
    color: var(--el-text-color-placeholder);
  }
}
.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
}
.card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.card__head {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
}
.card__name {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__per {
  color: var(--el-color-primary);
  font-weight: lp.$font-weight-semibold;
}
.card__muted {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
}
.card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: lp.$space-2;
  margin-top: lp.$space-3;
  .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
