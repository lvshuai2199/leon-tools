<script setup lang="ts">
/**
 * 手机底部固定操作栏，按钮 44px；会自动在页面末尾留出同样高度的空白（按钮 44 + 上下内边距 16）。
 * 有标签栏的页面放在标签栏上方（安全区在标签栏里）；不显示标签栏的页面贴底，底部加安全区（--lp-fixed-safe）。
 * 电脑上按 desktop 属性：inline = 普通一行右对齐；none = 不显示。
 */
import { useBreakpoint } from '@/composables/useBreakpoint'

withDefaults(defineProps<{ desktop?: 'inline' | 'none' }>(), { desktop: 'inline' })
const { isMobile } = useBreakpoint()
</script>

<template>
  <template v-if="isMobile">
    <div class="fab-spacer" aria-hidden="true" />
    <div class="fab">
      <slot />
    </div>
  </template>
  <div v-else-if="desktop === 'inline'" class="fab-inline">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.fab-spacer {
  height: calc(#{lp.$component-size-mobile} + #{lp.$space-4});
}
.fab {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--lp-fixed-bottom, env(safe-area-inset-bottom));
  z-index: 90;
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  padding: lp.$space-2 lp.$page-padding-mobile calc(#{lp.$space-2} + var(--lp-fixed-safe, 0px));
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  :deep(.el-button) {
    flex: 1;
    height: lp.$component-size-mobile;
    margin-left: 0;
  }
  :deep(.fab__side) {
    flex: none;
    color: var(--el-text-color-secondary);
    font-size: lp.$font-size-extra-small;
  }
}
.fab-inline {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: lp.$space-3;
  margin-top: lp.$space-5;
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
