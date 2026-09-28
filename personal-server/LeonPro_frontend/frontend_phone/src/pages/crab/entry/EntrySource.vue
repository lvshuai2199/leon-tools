<script setup lang="ts">
/** 录入来源：出货日期 + 粘贴识别 / 拍照识别 / 手动（两端共用） */
import { Camera } from '@element-plus/icons-vue'
import DateField from '@/components/DateField.vue'
import type { EntryTab } from './useCrabEntry'

const shipDate = defineModel<string>('shipDate', { required: true })
const tab = defineModel<EntryTab>('tab', { required: true })
const rawText = defineModel<string>('rawText', { required: true })
defineProps<{
  parsing: boolean
  ocrProgress: number | null
  manual: { customerName: string; phone: string; address: string; spec: string; quantity: string }
}>()
const emit = defineEmits<{ parse: []; paste: []; photo: [file: File | undefined]; addManual: [] }>()

const tabs = [
  { label: '粘贴识别', value: 'paste' },
  { label: '拍照识别', value: 'photo' },
  { label: '手动', value: 'manual' },
]
function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  emit('photo', file)
}
</script>

<template>
  <el-form label-position="top" class="src" @submit.prevent>
    <el-form-item label="出货日期">
      <DateField v-model="shipDate" title="出货日期" />
    </el-form-item>
    <el-segmented v-model="tab" :options="tabs" block class="src__tabs" />

    <template v-if="tab === 'paste'">
      <el-input
        v-model="rawText"
        type="textarea"
        :autosize="{ minRows: 6, maxRows: 14 }"
        placeholder="从微信或表格复制后粘贴：序号 姓名 电话 地址 规格 数量"
        class="src__area"
        @paste="emit('paste')"
      />
      <el-button type="primary" class="src__btn" :loading="parsing" @click="emit('parse')">识别文本</el-button>
    </template>

    <template v-else-if="tab === 'photo'">
      <p class="src__hint">拍订货表照片，识别需要十几秒。识别慢或不准时，可以用手机系统的「提取文字」复制后回到「粘贴识别」。</p>
      <label class="src__file" :class="{ 'is-busy': ocrProgress != null }">
        <el-icon :size="18"><Camera /></el-icon>
        <span>{{ ocrProgress != null ? `识别中 ${ocrProgress}%` : '拍照 / 从相册选' }}</span>
        <input accept="image/*" capture="environment" type="file" :disabled="ocrProgress != null" @change="onFile" />
      </label>
      <el-progress v-if="ocrProgress != null" :percentage="ocrProgress" :show-text="false" class="src__progress" />
    </template>

    <template v-else>
      <el-form-item label="姓名" required><el-input v-model="manual.customerName" placeholder="收货人姓名" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="manual.phone" inputmode="tel" placeholder="11 位手机号" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="manual.address" placeholder="收货地址" /></el-form-item>
      <div class="src__row">
        <el-form-item label="规格"><el-input v-model="manual.spec" placeholder="如 4两公" /></el-form-item>
        <el-form-item label="数量">
          <el-input v-model="manual.quantity" inputmode="numeric" placeholder="0"><template #suffix>只</template></el-input>
        </el-form-item>
      </div>
      <el-button type="primary" plain class="src__btn" @click="emit('addManual')">加入识别结果</el-button>
    </template>
  </el-form>
</template>

<style scoped lang="scss">
.src :deep(.el-form-item) {
  margin-bottom: lp.$space-4;
}
.src__tabs {
  margin-bottom: lp.$space-3;
}
.src__area {
  :deep(textarea) {
    font-size: lp.$font-size-base;
  }
}
.src__btn {
  width: 100%;
  margin-top: lp.$space-3;
}
.src__hint {
  margin: 0 0 lp.$space-3;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
}
.src__file {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: lp.$space-2;
  height: lp.$component-size-mobile;
  border-radius: lp.$radius-base;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  cursor: pointer;
  &.is-busy {
    background: var(--el-color-primary-light-5);
    cursor: progress;
  }
  input {
    display: none;
  }
}
.src__progress {
  margin-top: lp.$space-2;
}
.src__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: lp.$space-3;
}
</style>
