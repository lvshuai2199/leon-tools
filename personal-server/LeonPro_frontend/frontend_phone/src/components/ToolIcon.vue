<script setup lang="ts">
/** 工具图标：按菜单 icon 取 Element Plus 图标，螃蟹用蟹单橙底，其余用主色底 */
import { computed } from 'vue'
import { Grid, Key, Ship } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{ icon?: string; size?: number }>(), { icon: '', size: 44 })

const ICONS = { crab: Ship, key: Key } as const
const comp = computed(() => ICONS[props.icon as keyof typeof ICONS] || Grid)
const tone = computed(() => (props.icon === 'crab' ? 'crab' : 'primary'))
</script>

<template>
  <span class="ti" :class="`ti--${tone}`" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <el-icon :size="Math.round(size * 0.5)"><component :is="comp" /></el-icon>
  </span>
</template>

<style scoped lang="scss">
.ti {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: lp.$radius-base;
  color: var(--el-color-white);
}
.ti--crab {
  background: var(--lp-color-crab);
}
.ti--primary {
  background: var(--el-color-primary);
}
</style>
