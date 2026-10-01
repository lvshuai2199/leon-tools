<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight, Plus, Search } from '@element-plus/icons-vue'
import type { BadmintonBill } from '@/api/types'
import PageBar from '@/components/PageBar.vue'
import StateBlock from '@/components/StateBlock.vue'
import { formatMoney } from '@/utils/badminton-bill'
import { useBadmintonListPage, type DateMode } from './useBadmintonListPage'

const list = useBadmintonListPage()
const { filter, records, loading, loaded, error, summary } = list

const keyword = ref(filter.keyword)
const mode = computed({
  get: () => filter.mode,
  set: (v: DateMode) => list.setMode(v),
})
const range = computed({
  get: (): [string, string] => [filter.start, filter.end],
  set: (v: [string, string] | null) => {
    if (v && v[0] && v[1]) list.setRange(v[0], v[1])
  },
})

function doSearch() {
  list.search(keyword.value)
}
</script>

<template>
  <div class="bd-d">
    <PageBar title="羽毛球计费" back="/">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="list.goNew">新建球局</el-button>
      </template>
    </PageBar>

    <section class="toolbar">
      <el-radio-group v-model="mode">
        <el-radio-button value="day">单日</el-radio-button>
        <el-radio-button value="range">区间</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
      </el-radio-group>
      <div v-if="filter.mode === 'day'" class="toolbar__day">
        <el-button :icon="ArrowLeft" aria-label="前一天" @click="list.shift(-1)" />
        <el-date-picker
          :model-value="filter.date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          aria-label="球局日期"
          class="toolbar__date"
          @update:model-value="(v: string) => v && list.setDate(v)"
        />
        <el-button :icon="ArrowRight" aria-label="后一天" @click="list.shift(1)" />
      </div>
      <el-date-picker
        v-else-if="filter.mode === 'range'"
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        range-separator="至"
        :clearable="false"
        class="toolbar__range"
      />
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜标题，回车搜索"
        clearable
        class="toolbar__search"
        @keyup.enter="doSearch"
        @clear="doSearch"
      />
    </section>

    <p v-if="records.length" class="summary">
      共 {{ summary.count }} 局<span class="dot">·</span>合计 {{ summary.total }} 元
    </p>

    <div class="panel">
      <StateBlock v-if="loading && !records.length" type="loading" />
      <StateBlock v-else-if="error" type="error" title="加载失败" :desc="error" @retry="list.load()" />
      <StateBlock v-else-if="loaded && !records.length" type="empty" icon="list" :title="list.empty.value">
        <el-button type="primary" :icon="Plus" @click="list.goNew">新建球局</el-button>
      </StateBlock>
      <el-table
        v-else
        :data="records"
        row-key="id"
        class="table"
        :row-class-name="() => 'is-clickable'"
        @row-click="list.openDetail"
      >
        <el-table-column prop="playDate" label="日期" width="120" />
        <el-table-column label="标题" min-width="160">
          <template #default="{ row }">{{ row.title || '未填标题' }}</template>
        </el-table-column>
        <el-table-column prop="participantCount" label="人数" width="80" align="center" />
        <el-table-column label="场地费" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.courtTotal) }}</template>
        </el-table-column>
        <el-table-column label="用球费" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.ballTotal) }}</template>
        </el-table-column>
        <el-table-column label="总计" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.grandTotal) }}</template>
        </el-table-column>
        <el-table-column label="个人应付" width="120" align="right">
          <template #default="{ row }">
            <span class="per">{{ formatMoney(row.perPerson) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="right">
          <template #default="{ row }">
            <div class="ops lp-table-ops" @click.stop>
              <el-button link type="primary" @click="list.openDetail(row as BadmintonBill)">编辑</el-button>
              <el-button link type="primary" @click="list.exportSheet(row as BadmintonBill)">结算图</el-button>
              <el-button link type="danger" @click="list.remove(row as BadmintonBill)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.bd-d {
  padding-bottom: lp.$space-6;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: lp.$space-3;
  margin-bottom: lp.$space-3;
}
.toolbar__day {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  .el-button + .el-button {
    margin-left: 0;
  }
}
.toolbar__date {
  width: 160px;
}
.toolbar__range {
  max-width: 300px;
}
.toolbar__search {
  width: 240px;
  margin-left: auto;
}
.summary {
  margin: 0 0 lp.$space-3;
  color: var(--el-text-color-regular);
  .dot {
    margin: 0 lp.$space-2;
    color: var(--el-text-color-placeholder);
  }
}
.panel {
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  overflow: hidden;
}
.table {
  :deep(.is-clickable) {
    cursor: pointer;
  }
}
.per {
  font-weight: lp.$font-weight-semibold;
  color: var(--el-color-primary);
}
</style>
