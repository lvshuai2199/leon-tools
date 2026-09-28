<script setup lang="ts">
/**
 * 蟹单列表（电脑）：表格；名字一格里是可点的已付款/已发货标签；
 * 点某行打开 /crab/:id（同一页面上的 480 宽弹窗，关掉回到列表）。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowRight, Plus, Search } from '@element-plus/icons-vue'
import type { CrabShipment } from '@/api/types'
import PageBar from '@/components/PageBar.vue'
import StateBlock from '@/components/StateBlock.vue'
import CrabStatusTag from '../components/CrabStatusTag.vue'
import CrabDetailDialog from '../components/CrabDetailDialog.vue'
import { useCrabListPage } from './useCrabListPage'

const props = withDefaults(defineProps<{ detailId?: string | null }>(), { detailId: null })

const router = useRouter()
const { list, goEntry, openDetail, scan, pickImage, remove, share, exportSelected } = useCrabListPage()
const { filter, records, loading, loaded, error, summary, selectedIds } = list

const keyword = ref(filter.keyword)
const mode = computed({
  get: () => filter.mode,
  set: (v) => list.setMode(v),
})
const range = computed({
  get: (): [string, string] => [filter.start, filter.end],
  set: (v: [string, string] | null) => {
    if (v && v[0] && v[1]) list.setRange(v[0], v[1])
  },
})

function onSelection(rows: CrabShipment[]) {
  list.setSelected(rows.map((r) => r.id))
}
function onMore(cmd: string, row: CrabShipment) {
  if (cmd === 'image') pickImage(row as CrabShipment)
  else if (cmd === 'delete') remove(row as CrabShipment)
}
function closeDetail() {
  router.push({ path: '/crab', query: list.listQuery() })
}
function doSearch() {
  list.search(keyword.value)
}
</script>

<template>
  <div class="crab-d">
    <PageBar title="螃蟹出货" back="/">
      <template #actions>
        <el-button :disabled="!selectedIds.length" @click="exportSelected">
          导出发货图{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="goEntry">录入出货单</el-button>
      </template>
    </PageBar>

    <section class="toolbar">
      <el-radio-group v-model="mode">
        <el-radio-button value="day">单日</el-radio-button>
        <el-radio-button value="range">区间</el-radio-button>
      </el-radio-group>
      <div v-if="filter.mode === 'day'" class="toolbar__day">
        <el-button :icon="ArrowLeft" aria-label="前一天" @click="list.shift(-1)" />
        <el-date-picker
          :model-value="filter.date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          aria-label="出货日期"
          class="toolbar__date"
          @update:model-value="(v: string) => v && list.setDate(v)"
        />
        <el-button :icon="ArrowRight" aria-label="后一天" @click="list.shift(1)" />
      </div>
      <el-date-picker
        v-else
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
        placeholder="搜姓名 / 电话，回车搜索"
        clearable
        class="toolbar__search"
        @keyup.enter="doSearch"
        @clear="doSearch"
      />
    </section>

    <p v-if="records.length" class="summary">
      共 {{ summary.count }} 单<span class="dot">·</span>未付 {{ summary.unpaid }}<span class="dot">·</span>未发
      {{ summary.unshipped }}<span class="dot">·</span>合计 {{ summary.totalQty }} 只
    </p>

    <div class="panel">
      <StateBlock v-if="loading && !records.length" type="loading" />
      <StateBlock v-else-if="error" type="error" title="加载失败" :desc="error" @retry="list.load()" />
      <StateBlock v-else-if="loaded && !records.length" type="empty" :title="list.empty.value">
        <el-button type="primary" :icon="Plus" @click="goEntry">录入出货单</el-button>
      </StateBlock>
      <el-table
        v-else
        :data="records"
        row-key="id"
        class="table"
        :row-class-name="() => 'is-clickable'"
        @row-click="openDetail"
        @selection-change="onSelection"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column label="序号" width="64">
          <template #default="{ row }">{{ row.seqNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="姓名" min-width="230">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="name-cell__name">{{ row.customerName || '未填姓名' }}</span>
              <CrabStatusTag field="paid" :on="row.paid" @toggle="list.toggleStatus(row as CrabShipment, 'paid')" />
              <CrabStatusTag field="shipped" :on="row.shipped" @toggle="list.toggleStatus(row as CrabShipment, 'shipped')" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规格 / 数量" width="130">
          <template #default="{ row }">
            <span class="spec">{{ row.spec || '-' }} · {{ row.quantity || 0 }} 只</span>
          </template>
        </el-table-column>
        <el-table-column label="电话" width="136">
          <template #default="{ row }">
            <a v-if="row.phone" :href="`tel:${row.phone}`" class="phone" @click.stop>{{ row.phone }}</a>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="单号" min-width="150">
          <template #default="{ row }">
            <span v-if="row.trackingNo">{{ row.trackingNo }}</span>
            <span v-else class="muted">未填</span>
          </template>
        </el-table-column>
        <el-table-column v-if="filter.mode === 'range'" prop="shipDate" label="日期" width="110" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="ops" @click.stop>
              <el-button link type="primary" @click="scan(row as CrabShipment)">扫单号</el-button>
              <el-button link type="primary" @click="share(row as CrabShipment)">分享</el-button>
              <el-dropdown trigger="click" @command="(c: string) => onMore(c, row as CrabShipment)">
                <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="image">从图片识别单号</el-dropdown-item>
                    <el-dropdown-item command="delete" class="is-danger-item">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <CrabDetailDialog :id="props.detailId" @close="closeDetail" @changed="list.load()" />
  </div>
</template>

<style scoped lang="scss">
.crab-d {
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
.name-cell {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  flex-wrap: wrap;
}
.name-cell__name {
  font-weight: lp.$font-weight-medium;
  color: var(--el-text-color-primary);
}
.spec {
  color: var(--lp-color-crab-text);
  font-weight: lp.$font-weight-medium;
}
.phone {
  color: var(--el-color-primary);
}
.muted {
  color: var(--el-text-color-placeholder);
}
.ops {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  .el-button + .el-button {
    margin-left: 0;
  }
}
:global(.is-danger-item) {
  color: var(--el-color-danger);
}
</style>
