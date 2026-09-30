<script setup lang="ts">
/** 蟹单列表（手机）：卡片（点卡片进详情）；名字一行里是可点的已付款/已发货标签；按钮「分享」「更多」（更多里只有删除） */
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight, Delete, Finished, MoreFilled, Phone, Plus, Search } from '@element-plus/icons-vue'
import type { CrabShipment } from '@/api/types'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import DateField from '@/components/DateField.vue'
import StateBlock from '@/components/StateBlock.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import ActionSheet, { type SheetAction } from '@/components/ActionSheet.vue'
import CrabStatusTag from '../components/CrabStatusTag.vue'
import { useCrabListPage } from './useCrabListPage'

const { list, goEntry, openDetail, remove, share, exportSelected } = useCrabListPage()
const { filter, records, loading, loaded, error, summary, selecting, selectedIds, allSelected } = list

const keyword = ref(filter.keyword)
const modeOptions = [
  { label: '单日', value: 'day' },
  { label: '区间', value: 'range' },
]
const mode = computed({
  get: () => filter.mode,
  set: (v) => list.setMode(v),
})

/** 区间：结束日期不能早于开始，开始日期不能晚于结束（日期面板里置灰、不能点） */
const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const beforeStart = (d: Date) => !!filter.start && ymd(d) < filter.start
const afterEnd = (d: Date) => !!filter.end && ymd(d) > filter.end

const moreOpen = ref(false)
const moreItem = ref<CrabShipment | null>(null)
const moreActions: SheetAction[] = [{ key: 'delete', label: '删除', icon: Delete, danger: true }]
function openMore(item: CrabShipment) {
  moreItem.value = item
  moreOpen.value = true
}
function onMore(key: string) {
  const item = moreItem.value
  if (!item) return
  if (key === 'delete') remove(item)
}

function onCard(item: CrabShipment) {
  if (selecting.value) list.toggleItem(item.id)
  else openDetail(item)
}
function doSearch() {
  list.search(keyword.value)
}
</script>

<template>
  <div class="crab-m">
    <PageBar title="螃蟹出货" back="/">
      <template #actions>
        <IconAction :icon="Finished" :label="selecting ? '取消选择' : '选择出货单导出发货图'" :text="selecting ? '取消' : '选择'" @click="list.toggleSelecting()" />
        <IconAction v-if="!selecting" :icon="Plus" label="录入出货单" @click="goEntry" />
      </template>
    </PageBar>

    <section class="filters">
      <el-segmented v-model="mode" :options="modeOptions" block class="filters__mode" />
      <div v-if="filter.mode === 'day'" class="filters__day">
        <el-button :icon="ArrowLeft" aria-label="前一天" class="filters__shift" @click="list.shift(-1)" />
        <DateField :model-value="filter.date" title="出货日期" @change="list.setDate" />
        <el-button :icon="ArrowRight" aria-label="后一天" class="filters__shift" @click="list.shift(1)" />
      </div>
      <div v-else class="filters__range">
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
        placeholder="搜姓名 / 电话"
        clearable
        enterkeyhint="search"
        @keyup.enter="doSearch"
        @clear="doSearch"
      />
    </section>

    <p v-if="records.length" class="summary">
      共 {{ summary.count }} 单<span class="dot">·</span>未付 {{ summary.unpaid }}<span class="dot">·</span>未发
      {{ summary.unshipped }}<span class="dot">·</span>合计 {{ summary.totalQty }} 只
    </p>

    <StateBlock v-if="loading && !records.length" type="loading" compact />
    <StateBlock v-else-if="error" type="error" title="加载失败" :desc="error" @retry="list.load()" />
    <StateBlock v-else-if="loaded && !records.length" type="empty" icon="list" :title="list.empty.value">
      <el-button type="primary" :icon="Plus" @click="goEntry">录入出货单</el-button>
    </StateBlock>

    <ul v-else class="cards">
      <li v-for="item in records" :key="item.id">
        <article class="card" :class="{ 'is-picked': selectedIds.includes(item.id) }" @click="onCard(item)">
          <div class="card__head">
            <el-checkbox
              v-if="selecting"
              :model-value="selectedIds.includes(item.id)"
              class="card__check"
              :aria-label="`选择 ${item.customerName}`"
              @click.stop
              @change="list.toggleItem(item.id)"
            />
            <h3 class="card__name">{{ item.seqNo ? `${item.seqNo}. ` : '' }}{{ item.customerName || '未填姓名' }}</h3>
            <CrabStatusTag field="paid" :on="item.paid" :readonly="selecting" @toggle="list.toggleStatus(item, 'paid')" />
            <CrabStatusTag field="shipped" :on="item.shipped" :readonly="selecting" @toggle="list.toggleStatus(item, 'shipped')" />
          </div>
          <p class="card__spec">
            <span v-if="item.spec">{{ item.spec }}</span><span v-else class="card__missing">规格未填</span> · {{ item.quantity || 0 }} 只
            <span v-if="filter.mode === 'range'" class="card__date">{{ item.shipDate }}</span>
          </p>
          <a v-if="item.phone" :href="`tel:${item.phone}`" class="card__phone" @click.stop>
            <el-icon><Phone /></el-icon>{{ item.phone }}
          </a>
          <p v-else class="card__muted">电话 未填</p>
          <p v-if="item.address" class="card__addr">{{ item.address }}</p>
          <p v-else class="card__muted">地址 未填</p>
          <p v-if="item.trackingNo" class="card__muted">单号 {{ item.trackingNo }}</p>
          <p v-else class="card__muted">单号 未填</p>
          <div v-if="!selecting" class="card__actions" @click.stop>
            <el-button type="primary" plain @click="share(item)">分享</el-button>
            <el-button :icon="MoreFilled" aria-label="更多" @click="openMore(item)">更多</el-button>
          </div>
        </article>
      </li>
    </ul>

    <FixedActionBar v-if="selecting" desktop="none">
      <el-button @click="list.toggleAll()">{{ allSelected ? '取消全选' : '全选' }}</el-button>
      <el-button type="primary" :disabled="!selectedIds.length" @click="exportSelected">
        导出发货图（{{ selectedIds.length }}）
      </el-button>
    </FixedActionBar>

    <ActionSheet v-model="moreOpen" :title="moreItem ? moreItem.customerName || '出货单' : ''" :actions="moreActions" @select="onMore" />
  </div>
</template>

<style scoped lang="scss">
.crab-m {
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
  border: 1px solid transparent;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  &.is-picked {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}
.card__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: lp.$space-2;
}
.card__check {
  height: 28px;
}
.card__name {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__spec {
  margin: lp.$space-2 0 0;
  font-size: lp.$font-size-mobile-body;
  font-weight: lp.$font-weight-medium;
  color: var(--lp-color-crab-text);
}
.card__missing {
  font-weight: lp.$font-weight-regular;
  color: var(--el-text-color-secondary);
}
.card__date {
  margin-left: lp.$space-2;
  font-weight: lp.$font-weight-regular;
  color: var(--el-text-color-secondary);
}
.card__phone {
  display: inline-flex;
  align-items: center;
  gap: lp.$space-1;
  min-height: 32px;
  margin-top: lp.$space-1;
  color: var(--el-color-primary);
  font-size: lp.$font-size-mobile-body;
}
.card__addr {
  margin: lp.$space-1 0 0;
  color: var(--el-text-color-regular);
  word-break: break-all;
}
.card__muted {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
  word-break: break-all;
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
