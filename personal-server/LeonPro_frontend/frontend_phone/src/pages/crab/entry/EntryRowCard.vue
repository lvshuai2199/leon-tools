<script setup lang="ts">
/** 识别结果里的一条：字段带标签，数量带单位「只」；右上角删除图标（点击区 44px） */
import { Delete } from '@element-plus/icons-vue'
import type { EntryRow } from './useCrabEntry'

const props = defineProps<{ row: EntryRow; index: number }>()
defineEmits<{ remove: [] }>()
const r = props.row
</script>

<template>
  <article class="erc">
    <header class="erc__head">
      <span class="erc__no">第 {{ index + 1 }} 条</span>
      <button type="button" class="erc__del" :aria-label="`删除第 ${index + 1} 条`" @click="$emit('remove')">
        <el-icon :size="18"><Delete /></el-icon>
      </button>
    </header>
    <el-form label-position="top" class="erc__form" @submit.prevent>
      <div class="erc__row erc__row--2">
        <el-form-item label="姓名" required><el-input v-model="r.customerName" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="r.phone" inputmode="tel" placeholder="手机号" /></el-form-item>
      </div>
      <el-form-item label="地址"><el-input v-model="r.address" placeholder="收货地址" /></el-form-item>
      <div class="erc__row erc__row--qty">
        <el-form-item label="规格"><el-input v-model="r.spec" placeholder="如 4两公" /></el-form-item>
        <el-form-item label="数量">
          <el-input v-model="r.quantity" inputmode="numeric" placeholder="0"><template #suffix>只</template></el-input>
        </el-form-item>
      </div>
    </el-form>
  </article>
</template>

<style scoped lang="scss">
.erc {
  position: relative;
  padding: lp.$space-3 lp.$card-padding lp.$space-1;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.erc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: lp.$space-1;
}
.erc__no {
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.erc__del {
  position: absolute;
  top: 2px;
  right: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: lp.$component-size-mobile;
  height: lp.$component-size-mobile;
  padding: 0;
  border: none;
  border-radius: lp.$radius-base;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  &:hover,
  &:focus-visible {
    color: var(--el-color-danger);
  }
}
.erc__form :deep(.el-form-item) {
  margin-bottom: lp.$space-3;
}
.erc__row {
  display: grid;
  gap: lp.$space-3;
}
.erc__row--2 {
  grid-template-columns: 1fr;
  @media (min-width: 420px) {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}
.erc__row--qty {
  grid-template-columns: minmax(0, 1fr) 120px;
}
</style>
