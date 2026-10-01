<script setup lang="ts">
import type { BadmintonBill } from '@/api/types'
import { ballAmount, courtAmount, formatMoney, summarize } from '@/utils/badminton-bill'

defineProps<{
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
</script>

<template>
  <el-form :model="form" label-width="88px" class="bill-form" :class="{ 'is-compact': compact }">
    <el-form-item label="日期">
      <el-date-picker v-model="form.playDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
    </el-form-item>
    <el-form-item label="标题">
      <el-input v-model="form.title" maxlength="100" placeholder="如 周五夜场 / 体育馆" />
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
    <div v-for="(row, index) in form.ballItems" :key="'b' + index" class="item">
      <el-form-item label="品牌">
        <el-input v-model="row.brand" maxlength="50" placeholder="如 亚狮龙7号" />
      </el-form-item>
      <div class="item__grid">
        <el-form-item label="数量">
          <el-input-number v-model="row.quantity" :min="0" :max="10000" :step="1" controls-position="right" class="w-full" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="row.unitPrice" :min="0" :max="100000" :step="1" :precision="2" controls-position="right" class="w-full" />
        </el-form-item>
      </div>
      <div class="item__foot">
        <span>小计 {{ formatMoney(ballAmount(row)) }}</span>
        <el-button type="danger" link @click="emit('removeBall', index)">删除</el-button>
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
.is-compact {
  :deep(.el-form-item) {
    margin-bottom: lp.$space-2;
  }
}
</style>
