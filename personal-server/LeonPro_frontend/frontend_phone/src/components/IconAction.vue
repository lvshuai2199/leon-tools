<script setup lang="ts">
/** 手机顶栏右侧的 44×44 图标按钮（带无障碍名称，可选显示一两个字） */
import type { Component } from 'vue'

withDefaults(defineProps<{ icon: Component; label: string; text?: string; disabled?: boolean }>(), { text: '', disabled: false })
defineEmits<{ click: [e: MouseEvent] }>()
</script>

<template>
  <button type="button" class="ia" :aria-label="label" :title="label" :disabled="disabled" @click="$emit('click', $event)">
    <el-icon :size="20"><component :is="icon" /></el-icon>
    <span v-if="text" class="ia__text">{{ text }}</span>
  </button>
</template>

<style scoped lang="scss">
.ia {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: lp.$component-size-mobile;
  height: lp.$component-size-mobile;
  padding: 0 lp.$space-1;
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  font-size: lp.$font-size-base;
  cursor: pointer;
  &:disabled {
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
  }
}
</style>
