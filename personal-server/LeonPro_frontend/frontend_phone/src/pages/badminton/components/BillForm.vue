<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import badmintonApi from '@/api/badminton'
import type { BadmintonBallFeeItem, BadmintonBill } from '@/api/types'
import {
  ballAmount,
  bucketPrice,
  bucketUnitPrice,
  courtAmount,
  formatMoney,
  summarize,
} from '@/utils/badminton-bill'

const props = defineProps<{
  form: BadmintonBill
  compact?: boolean
}>()

const emit = defineEmits<{
  addCourt: []
  removeCourt: [index: number]
  addBall: []
  removeBall: [index: number]
  delete: []
}>()

/* ---------- 名称预览：调接口（与保存规则一致），防抖 300ms；没回或失败显示兜底文案 ---------- */
const NAME_FALLBACK = '保存时自动加上日期'
const previewName = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | undefined
let seq = 0

function requestPreview() {
  const mine = ++seq
  controller?.abort()
  controller = new AbortController()
  badmintonApi
    .namePreview(
      { id: props.form.id || undefined, title: props.form.title || '', playDate: props.form.playDate || '' },
      controller.signal,
    )
    .then((res) => {
      if (mine === seq) previewName.value = res?.name || ''
    })
    .catch(() => {
      if (mine === seq) previewName.value = ''
    })
}

watch(
  () => [props.form.title, props.form.playDate, props.form.id],
  () => {
    previewName.value = ''
    if (timer) clearTimeout(timer)
    timer = setTimeout(requestPreview, 300)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  controller?.abort()
})

/* ---------- 整桶价：有值时单价只读 = round2(整桶/12)；「改为手填」清空整桶价 ---------- */
function hasBucket(row: BadmintonBallFeeItem) {
  return bucketPrice(row) !== null
}

function unitOf(row: BadmintonBallFeeItem) {
  const bucket = bucketPrice(row)
  return bucket === null ? Number(row.unitPrice || 0) : bucketUnitPrice(bucket)
}

function onBucketChange(row: BadmintonBallFeeItem, value: number | null | undefined) {
  const bucket = bucketPrice({ bucketPrice: value ?? null })
  row.bucketPrice = value === undefined ? null : value
  if (bucket !== null) row.unitPrice = bucketUnitPrice(bucket)
}

function manualUnit(row: BadmintonBallFeeItem) {
  const bucket = bucketPrice(row)
  if (bucket !== null) row.unitPrice = bucketUnitPrice(bucket)
  row.bucketPrice = null
}
</script>

<template>
  <el-form :model="form" label-width="88px" class="bill-form" :class="{ 'is-compact': compact }">
    <el-form-item label="日期">
      <el-date-picker v-model="form.playDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
    </el-form-item>
    <el-form-item label="名称">
      <div class="name-field">
        <el-input v-model="form.title" maxlength="100" placeholder="不填则用日期" />
        <p class="name-preview">{{ previewName ? `保存为：${previewName}` : NAME_FALLBACK }}</p>
      </div>
    </el-form-item>
    <el-form-item label="参与人数">
      <el-input-number
        v-model="form.participantCount"
        :min="1"
        :max="999"
        :step="1"
        controls-position="right"
        class="w-full"
      />
    </el-form-item>

    <div class="block-head">
      <span>场地费</span>
      <el-button type="primary" link @click="emit('addCourt')">添加场地</el-button>
    </div>
    <div v-for="(row, index) in form.courtItems" :key="'c' + index" class="item">
      <div class="item__grid">
        <el-form-item label="片数">
          <el-input-number v-model="row.courtCount" :min="0" :max="100" :step="1" controls-position="right" class="w-full" />
        </el-form-item>
        <el-form-item label="小时">
          <el-input-number v-model="row.hours" :min="0" :max="24" :step="0.5" :precision="2" controls-position="right" class="w-full" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="row.unitPrice" :min="0" :max="100000" :step="1" :precision="2" controls-position="right" class="w-full" />
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="row.remark" maxlength="100" placeholder="场地名 / 时段" />
      </el-form-item>
      <div class="item__foot">
        <span>小计 {{ formatMoney(courtAmount(row)) }}</span>
        <el-button type="danger" link @click="emit('removeCourt', index)">删除</el-button>
      </div>
    </div>

    <div class="block-head">
      <span>用球费用</span>
      <el-button type="primary" link @click="emit('addBall')">添加用球</el-button>
    </div>
    <!-- 电脑端：一行一条，列 = 品牌 / 数量 / 单价 / 整桶价 / 小计 -->
    <div v-if="!compact" class="balls">
      <div class="balls__row balls__row--head">
        <span>品牌</span>
        <span>数量</span>
        <span>单价</span>
        <span>整桶价</span>
        <span class="is-right">小计</span>
        <span />
      </div>
      <div v-for="(row, index) in form.ballItems" :key="'b' + index" class="balls__row">
        <el-input v-model="row.brand" maxlength="50" placeholder="如 亚狮龙7号" aria-label="品牌" />
        <el-input-number
          v-model="row.quantity"
          :min="0"
          :max="10000"
          :step="1"
          controls-position="right"
          class="w-full"
          aria-label="数量"
        />
        <div class="unit">
          <template v-if="hasBucket(row)">
            <div class="unit__ro">¥{{ formatMoney(unitOf(row)) }}</div>
            <div class="unit__hint">
              <span>整桶 ÷ 12</span>
              <el-button type="primary" link class="unit__manual" @click="manualUnit(row)">改为手填</el-button>
            </div>
          </template>
          <el-input-number
            v-else
            v-model="row.unitPrice"
            :min="0"
            :max="100000"
            :step="1"
            :precision="2"
            controls-position="right"
            class="w-full"
            aria-label="单价"
          />
        </div>
        <el-input-number
          :model-value="row.bucketPrice ?? undefined"
          :min="0"
          :max="100000"
          :precision="2"
          :controls="false"
          :value-on-clear="null"
          class="bucket"
          aria-label="整桶价"
          @update:model-value="(v: number | null | undefined) => onBucketChange(row, v)"
        >
          <template #suffix>元/桶</template>
        </el-input-number>
        <span class="balls__amount">{{ formatMoney(ballAmount(row)) }}</span>
        <el-button type="danger" link @click="emit('removeBall', index)">删除</el-button>
      </div>
    </div>

    <!-- 手机端：每条一张卡片；第一行品牌 + 数量，第二行单价 / 整桶价各半，小计在右下角 -->
    <div v-for="(row, index) in compact ? form.ballItems : []" :key="'bm' + index" class="ball-card">
      <div class="ball-card__row ball-card__row--first">
        <label class="field">
          <span class="field__label">品牌</span>
          <el-input v-model="row.brand" maxlength="50" placeholder="如 亚狮龙7号" />
        </label>
        <label class="field">
          <span class="field__label">数量</span>
          <el-input-number v-model="row.quantity" :min="0" :max="10000" :step="1" controls-position="right" class="w-full" />
        </label>
      </div>
      <div class="ball-card__row">
        <div class="field">
          <span class="field__label">单价</span>
          <template v-if="hasBucket(row)">
            <div class="unit__ro">¥{{ formatMoney(unitOf(row)) }}</div>
            <div class="unit__hint">
              <span>整桶 ÷ 12</span>
              <el-button type="primary" link class="unit__manual" @click="manualUnit(row)">改为手填</el-button>
            </div>
          </template>
          <el-input-number
            v-else
            v-model="row.unitPrice"
            :min="0"
            :max="100000"
            :step="1"
            :precision="2"
            controls-position="right"
            class="w-full"
          />
        </div>
        <label class="field">
          <span class="field__label">整桶价</span>
          <el-input-number
            :model-value="row.bucketPrice ?? undefined"
            :min="0"
            :max="100000"
            :precision="2"
            :controls="false"
            :value-on-clear="null"
            class="w-full"
            @update:model-value="(v: number | null | undefined) => onBucketChange(row, v)"
          >
            <template #suffix>元/桶</template>
          </el-input-number>
        </label>
      </div>
      <div class="ball-card__foot">
        <el-button type="danger" link @click="emit('removeBall', index)">删除</el-button>
        <span class="ball-card__amount">小计 {{ formatMoney(ballAmount(row)) }}</span>
      </div>
    </div>

    <el-form-item label="备注">
      <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="可选" />
    </el-form-item>

    <div class="totals">
      <div><span>场地费</span><strong>{{ formatMoney(summarize(form).courtTotal) }}</strong></div>
      <div><span>用球费用</span><strong>{{ formatMoney(summarize(form).ballTotal) }}</strong></div>
      <div><span>总费用</span><strong>{{ formatMoney(summarize(form).grandTotal) }}</strong></div>
      <div class="is-hl">
        <span>个人应付（{{ summarize(form).people }}人）</span>
        <strong>{{ formatMoney(summarize(form).perPerson) }}</strong>
      </div>
    </div>

    <el-button v-if="form.id" type="danger" link class="del" @click="emit('delete')">删除球局</el-button>
  </el-form>
</template>

<style scoped lang="scss">
.w-full {
  width: 100%;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: lp.$space-3 0 lp.$space-2;
  font-weight: lp.$font-weight-semibold;
}
.item {
  margin-bottom: lp.$space-3;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
}
.item__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: lp.$space-2;
  @include lp.mobile {
    grid-template-columns: 1fr;
  }
}
.item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-regular);
}
.totals {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: lp.$space-2;
  margin-top: lp.$space-3;
  @include lp.mobile {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  > div {
    padding: lp.$space-3;
    border-radius: lp.$radius-base;
    background: var(--el-fill-color-light);
    span {
      display: block;
      font-size: lp.$font-size-base;
      color: var(--el-text-color-secondary);
    }
    strong {
      display: block;
      margin-top: 4px;
      font-size: lp.$font-size-medium;
    }
  }
  .is-hl {
    background: var(--el-color-primary-light-9);
    strong {
      color: var(--el-color-primary);
    }
  }
}
.del {
  margin-top: lp.$space-3;
}
.name-field {
  width: 100%;
}
.name-preview {
  margin: lp.$space-1 0 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
.balls {
  margin-bottom: lp.$space-3;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
}
.balls__row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 110px 140px 120px 90px 40px;
  gap: lp.$space-2;
  align-items: start;
  & + & {
    margin-top: lp.$space-2;
  }
}
.balls__row--head {
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.is-right,
.balls__amount {
  text-align: right;
}
.balls__amount {
  line-height: 32px;
  font-variant-numeric: tabular-nums;
}
.bucket {
  width: 120px;
}
.unit__ro {
  line-height: 32px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.unit__hint {
  display: flex;
  align-items: center;
  gap: lp.$space-1;
  font-size: 12px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}
.unit__manual {
  height: auto;
  padding: 0;
  font-size: 12px;
}
.ball-card {
  margin-bottom: lp.$space-3;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
}
.ball-card__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: lp.$space-2;
  & + & {
    margin-top: lp.$space-2;
  }
}
.ball-card__row--first {
  grid-template-columns: minmax(0, 1fr) 120px;
}
.field {
  display: block;
  min-width: 0;
}
.field__label {
  display: block;
  margin-bottom: lp.$space-1;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.ball-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: lp.$space-2;
}
.ball-card__amount {
  font-weight: lp.$font-weight-semibold;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.is-compact {
  :deep(.el-form-item) {
    margin-bottom: lp.$space-2;
  }
}
</style>
