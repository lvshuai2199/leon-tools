<script setup lang="ts">
/**
 * 单个日期（YYYY-MM-DD）：电脑用 el-date-picker；手机点开底部抽屉里的日历面板，选中即关闭。
 */
import { computed, ref } from 'vue'
import { Calendar } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

const model = defineModel<string>({ required: true })
const props = withDefaults(
  defineProps<{ title?: string; placeholder?: string; clearable?: boolean; disabled?: boolean; ariaLabel?: string }>(),
  { title: '选择日期', placeholder: '选择日期', clearable: false, disabled: false, ariaLabel: '' },
)
const emit = defineEmits<{ change: [value: string] }>()
const { isMobile } = useBreakpoint()

const open = ref(false)
const WEEK = ['日', '一', '二', '三', '四', '五', '六']
const display = computed(() => {
  if (!model.value) return ''
  const d = new Date(`${model.value}T00:00:00`)
  return Number.isNaN(d.getTime()) ? model.value : `${model.value} 周${WEEK[d.getDay()]}`
})

function onPanelPick(v: string | null) {
  if (!v) return
  open.value = false
  if (v !== model.value) {
    model.value = v
    emit('change', v)
  }
}
function onPickerChange(v: string | null) {
  model.value = v || ''
  emit('change', model.value)
}
</script>

<template>
  <template v-if="isMobile">
    <button type="button" class="df-trigger" :disabled="disabled" :aria-label="ariaLabel || title" @click="open = true">
      <el-icon class="df-trigger__icon"><Calendar /></el-icon>
      <span class="df-trigger__text" :class="{ 'is-placeholder': !model }">{{ display || placeholder }}</span>
    </button>
    <el-drawer v-model="open" direction="btt" size="auto" :title="props.title" append-to-body class="lp-date-sheet">
      <el-date-picker-panel
        :model-value="model"
        type="date"
        value-format="YYYY-MM-DD"
        :border="false"
        class="df-panel"
        @update:model-value="onPanelPick"
      />
    </el-drawer>
  </template>
  <el-date-picker
    v-else
    :model-value="model"
    type="date"
    value-format="YYYY-MM-DD"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :aria-label="ariaLabel || title"
    class="df-picker"
    @update:model-value="onPickerChange"
  />
</template>

<style scoped lang="scss">
.df-trigger {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  width: 100%;
  height: lp.$component-size-mobile;
  padding: 0 lp.$space-3;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  text-align: left;
  cursor: pointer;
}
.df-trigger__icon {
  flex: none;
  color: var(--el-text-color-secondary);
}
.df-trigger__text {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  &.is-placeholder {
    color: var(--el-text-color-placeholder);
  }
}
.df-panel {
  width: 100%;
  :deep(.el-picker-panel__body),
  :deep(.el-date-picker__header),
  :deep(.el-picker-panel__content) {
    width: auto;
  }
  :deep(.el-picker-panel__content) {
    margin: 0;
  }
  :deep(.el-date-table) {
    width: 100%;
  }
  :deep(.el-date-table td) {
    height: 44px;
  }
}
.df-picker {
  width: 100%;
}
</style>
