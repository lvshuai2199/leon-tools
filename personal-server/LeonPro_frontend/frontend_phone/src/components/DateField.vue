<script setup lang="ts">
/**
 * 单个日期（YYYY-MM-DD）：电脑用 el-date-picker；手机点开底部抽屉里的日历面板，选中即关闭。
 * 手机日历只留切月的 ‹ ›（去掉切年的 « »，避免挨太近点错），箭头可点区 44×44；点标题里的年份可以换年。
 * disabledDate：不能选的日期置灰（区间的结束日期不能早于开始）；showWeek=false 时入口不显示「周几」（区间两个入口并排时用）。
 * markDate：在日历里标出这一天（区间结束抽屉里标出开始日：#E9EFFD 底、#2563EB 字）。
 * 手机日历不显示上个月、下个月的日子，只留本月，免得和置灰的日子分不开。
 */
import { computed, ref } from 'vue'
import { Calendar } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

const model = defineModel<string>({ required: true })
const props = withDefaults(
  defineProps<{
    title?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    ariaLabel?: string
    disabledDate?: (date: Date) => boolean
    showWeek?: boolean
    markDate?: string
  }>(),
  {
    title: '选择日期',
    placeholder: '选择日期',
    clearable: false,
    disabled: false,
    ariaLabel: '',
    disabledDate: undefined,
    showWeek: true,
    markDate: '',
  },
)
const emit = defineEmits<{ change: [value: string] }>()
const { isMobile } = useBreakpoint()

const open = ref(false)
const WEEK = ['日', '一', '二', '三', '四', '五', '六']
const display = computed(() => {
  if (!model.value) return ''
  const d = new Date(`${model.value}T00:00:00`)
  if (Number.isNaN(d.getTime()) || !props.showWeek) return model.value
  return `${model.value} 周${WEEK[d.getDay()]}`
})

const pad = (n: number) => String(n).padStart(2, '0')
/** 日历格子的额外 class：markDate 那天加 is-marked */
function cellClass(d: Date) {
  if (!props.markDate) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` === props.markDate ? 'is-marked' : ''
}

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
        :disabled-date="disabledDate"
        :cell-class-name="cellClass"
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
    :disabled-date="disabledDate"
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
  /* 只留切月箭头 ‹ ›，可点区 44×44，放在标题两端 */
  :deep(.el-date-picker__header) {
    display: flex;
    align-items: center;
    margin: 0 0 lp.$space-2;
    padding: 0;
  }
  :deep(.el-date-picker__prev-btn) {
    margin-right: auto;
  }
  :deep(.el-date-picker__next-btn) {
    margin-left: auto;
  }
  :deep(.el-picker-panel__icon-btn.d-arrow-left),
  :deep(.el-picker-panel__icon-btn.d-arrow-right) {
    display: none;
  }
  :deep(.el-date-picker__prev-btn),
  :deep(.el-date-picker__next-btn) {
    float: none;
    display: flex;
  }
  :deep(.el-picker-panel__icon-btn.arrow-left),
  :deep(.el-picker-panel__icon-btn.arrow-right) {
    width: 44px;
    height: 44px;
    margin: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
  }
  :deep(.el-date-picker__header-label) {
    font-size: lp.$font-size-medium;
  }
  /* 只显示本月的日子：上个月、下个月的格子留空、不能点 */
  :deep(.el-date-table td.prev-month),
  :deep(.el-date-table td.next-month) {
    visibility: hidden;
    pointer-events: none;
  }
  /* markDate（区间起点）：浅蓝底、主色字 */
  :deep(.el-date-table td.is-marked:not(.current) .el-date-table-cell__text) {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: lp.$font-weight-medium;
  }
  /* 不能选的日期（区间结束早于开始）：置灰 */
  :deep(.el-date-table td.disabled .el-date-table-cell) {
    background: transparent;
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
  }
}
.df-picker {
  width: 100%;
}
</style>
