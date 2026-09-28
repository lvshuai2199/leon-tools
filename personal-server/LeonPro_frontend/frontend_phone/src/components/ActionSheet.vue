<script setup lang="ts">
/** 手机「更多」操作：底部抽屉列表，每项 48px，危险操作红色，底部「取消」 */
import type { Component } from 'vue'

export interface SheetAction {
  key: string
  label: string
  icon?: Component
  danger?: boolean
  disabled?: boolean
}

const open = defineModel<boolean>({ required: true })
withDefaults(defineProps<{ actions: SheetAction[]; title?: string }>(), { title: '' })
const emit = defineEmits<{ select: [key: string] }>()

function pick(a: SheetAction) {
  if (a.disabled) return
  open.value = false
  emit('select', a.key)
}
</script>

<template>
  <el-drawer
    v-model="open"
    direction="btt"
    size="auto"
    :with-header="!!title"
    :title="title"
    append-to-body
    class="lp-action-sheet"
  >
    <ul class="as-list">
      <li v-for="a in actions" :key="a.key">
        <button
          type="button"
          class="as-item"
          :class="{ 'is-danger': a.danger }"
          :disabled="a.disabled"
          @click="pick(a)"
        >
          <el-icon v-if="a.icon" :size="18"><component :is="a.icon" /></el-icon>
          <span>{{ a.label }}</span>
        </button>
      </li>
    </ul>
    <el-button class="as-cancel" size="large" @click="open = false">取消</el-button>
  </el-drawer>
</template>

<style scoped lang="scss">
.as-list {
  list-style: none;
  margin: 0 0 lp.$space-3;
  padding: 0;
}
.as-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: lp.$space-2;
  width: 100%;
  min-height: 48px;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  cursor: pointer;
  &.is-danger {
    color: var(--el-color-danger);
  }
  &:disabled {
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
  }
}
li:last-child .as-item {
  border-bottom: none;
}
.as-cancel {
  width: 100%;
}
</style>
