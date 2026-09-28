<script setup lang="ts">
/**
 * 蟹单表单（手机详情页、电脑详情弹窗共用）。标签在上；规格、数量一行；单号旁「扫单号」。
 * 删除放在表单最底部，红色文字按钮（点了先确认）。
 */
import { useBreakpoint } from '@/composables/useBreakpoint'
import DateField from '@/components/DateField.vue'
import CrabStatusTag from './CrabStatusTag.vue'
import type { CrabFormModel } from './useCrabForm'

const props = withDefaults(defineProps<{ form: CrabFormModel; showDelete?: boolean; showImage?: boolean }>(), {
  showDelete: true,
  showImage: false,
})
defineEmits<{ scan: []; pickImage: []; delete: [] }>()
const { isMobile } = useBreakpoint()
const f = props.form
</script>

<template>
  <el-form label-position="top" class="crab-form" :class="{ 'crab-form--mobile': isMobile }" @submit.prevent>
    <el-form-item label="出货日期">
      <DateField v-model="f.shipDate" title="出货日期" />
    </el-form-item>
    <el-form-item label="姓名" required>
      <el-input v-model="f.customerName" placeholder="收货人姓名" maxlength="30" />
    </el-form-item>
    <el-form-item label="电话">
      <el-input v-model="f.phone" inputmode="tel" placeholder="11 位手机号" maxlength="20" />
    </el-form-item>
    <el-form-item label="地址">
      <el-input v-model="f.address" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="收货地址" />
    </el-form-item>
    <div class="crab-form__row">
      <el-form-item label="规格" class="crab-form__spec">
        <el-input v-model="f.spec" placeholder="如 4两公" maxlength="20" />
      </el-form-item>
      <el-form-item label="数量" class="crab-form__qty">
        <el-input v-model="f.quantity" inputmode="numeric" placeholder="0" maxlength="6">
          <template #suffix>只</template>
        </el-input>
      </el-form-item>
    </div>
    <el-form-item label="发货单号">
      <div class="crab-form__track">
        <el-input v-model="f.trackingNo" placeholder="快递单号" clearable />
        <el-button type="primary" plain @click="$emit('scan')">扫单号</el-button>
        <el-button v-if="showImage" type="primary" plain @click="$emit('pickImage')">图片识别</el-button>
      </div>
    </el-form-item>
    <el-form-item label="状态">
      <div class="crab-form__status">
        <CrabStatusTag field="paid" :on="f.paid" size="large" @toggle="f.paid = f.paid ? 0 : 1" />
        <CrabStatusTag field="shipped" :on="f.shipped" size="large" @toggle="f.shipped = f.shipped ? 0 : 1" />
      </div>
    </el-form-item>
    <div v-if="showDelete && f.id" class="crab-form__danger">
      <el-button type="danger" text @click="$emit('delete')">删除这张出货单</el-button>
    </div>
  </el-form>
</template>

<style scoped lang="scss">
.crab-form {
  :deep(.el-form-item) {
    margin-bottom: lp.$space-4;
  }
  :deep(.el-form-item__label) {
    color: var(--el-text-color-regular);
  }
}
.crab-form__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: lp.$space-3;
}
.crab-form__track {
  display: flex;
  gap: lp.$space-2;
  width: 100%;
  .el-input {
    flex: 1;
    min-width: 0;
  }
  .el-button + .el-button {
    margin-left: 0;
  }
}
.crab-form__status {
  display: flex;
  gap: lp.$space-3;
}
.crab-form__danger {
  display: flex;
  justify-content: center;
  padding-top: lp.$space-2;
  border-top: 1px solid var(--el-border-color-lighter);
  .el-button {
    min-height: lp.$component-size-mobile;
  }
}
</style>
