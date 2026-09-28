<script setup lang="ts" generic="T extends string | number">
/**
 * 下拉选择：电脑用 el-select；手机点开底部抽屉，每项 48px，当前项带勾。
 */
import { computed, ref } from 'vue'
import { ArrowDown, Check } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

export interface SheetOption<V> {
  label: string
  value: V
  /** 选项下方的灰色小字 */
  desc?: string
  disabled?: boolean
}

const model = defineModel<T | ''>({ required: true })
const props = withDefaults(
  defineProps<{ options: SheetOption<T>[]; title?: string; placeholder?: string; disabled?: boolean }>(),
  { title: '请选择', placeholder: '请选择', disabled: false },
)
const emit = defineEmits<{ change: [value: T] }>()
const { isMobile } = useBreakpoint()

const open = ref(false)
const current = computed(() => props.options.find((o) => o.value === model.value))

function pick(o: SheetOption<T>) {
  if (o.disabled) return
  open.value = false
  if (o.value !== model.value) {
    model.value = o.value
    emit('change', o.value)
  }
}
function onSelect(v: T) {
  emit('change', v)
}
</script>

<template>
  <template v-if="isMobile">
    <button type="button" class="ss-trigger" :disabled="disabled" @click="open = true">
      <span class="ss-trigger__text" :class="{ 'is-placeholder': !current }">{{ current?.label || placeholder }}</span>
      <el-icon class="ss-trigger__arrow"><ArrowDown /></el-icon>
    </button>
    <el-drawer v-model="open" direction="btt" size="auto" :title="title" append-to-body class="lp-sheet-select">
      <ul class="ss-list" role="listbox">
        <li v-for="o in options" :key="String(o.value)">
          <button
            type="button"
            role="option"
            class="ss-item"
            :class="{ 'is-active': o.value === model, 'is-disabled': o.disabled }"
            :aria-selected="o.value === model"
            :disabled="o.disabled"
            @click="pick(o)"
          >
            <span class="ss-item__main">
              <span class="ss-item__label">{{ o.label }}</span>
              <span v-if="o.desc" class="ss-item__desc">{{ o.desc }}</span>
            </span>
            <el-icon v-if="o.value === model" class="ss-item__check"><Check /></el-icon>
          </button>
        </li>
      </ul>
    </el-drawer>
  </template>
  <el-select v-else v-model="model" :placeholder="placeholder" :disabled="disabled" class="ss-select" @change="onSelect">
    <el-option v-for="o in options" :key="String(o.value)" :label="o.label" :value="o.value" :disabled="o.disabled">
      <span>{{ o.label }}</span>
      <span v-if="o.desc" class="ss-option-desc">{{ o.desc }}</span>
    </el-option>
  </el-select>
</template>

<style scoped lang="scss">
.ss-trigger {
  display: flex;
  align-items: center;
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
  &:disabled {
    background: var(--el-disabled-bg-color);
    color: var(--el-text-color-placeholder);
  }
}
.ss-trigger__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  &.is-placeholder {
    color: var(--el-text-color-placeholder);
  }
}
.ss-trigger__arrow {
  color: var(--el-text-color-placeholder);
}
.ss-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ss-item {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  width: 100%;
  min-height: 48px;
  padding: lp.$space-2 lp.$space-1;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  text-align: left;
  cursor: pointer;
  &.is-active {
    color: var(--el-color-primary);
    font-weight: lp.$font-weight-medium;
  }
  &.is-disabled {
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
  }
}
li:last-child .ss-item {
  border-bottom: none;
}
.ss-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.ss-item__desc {
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
  font-weight: lp.$font-weight-regular;
}
.ss-item__check {
  flex: none;
}
.ss-select {
  width: 100%;
}
.ss-option-desc {
  margin-left: lp.$space-2;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
</style>
