<script setup lang="ts">
/**
 * 通用状态块（沿用原展示端 portal 的样式，颜色换成主题变量）
 * - empty：空状态（次要文字色图片图标）
 * - notfound：不存在 / 未公开（次要文字色，无重试；操作按钮通过默认插槽传入）
 * - error：加载失败（危险红感叹号 + 重试按钮）
 * - loading：加载中
 */
withDefaults(
  defineProps<{
    type?: 'empty' | 'notfound' | 'error' | 'loading'
    title?: string
    desc?: string
    compact?: boolean
  }>(),
  { type: 'empty', compact: false },
)
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state" :class="[`state--${type}`, { 'state--compact': compact }]" :role="type === 'error' ? 'alert' : undefined">
    <!-- 图标统一 48×48、线宽 2 -->
    <div class="state__icon" aria-hidden="true">
      <span v-if="type === 'loading'" class="spinner" />
      <svg v-else-if="type === 'notfound'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="21" cy="21" r="13" />
        <path d="M30.5 30.5 40 40" />
        <path d="M16 21h10" />
      </svg>
      <svg v-else-if="type === 'error'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="24" cy="24" r="18" />
        <path d="M24 15v11" />
        <circle cx="24" cy="32.5" r="1.5" fill="currentColor" stroke="none" />
      </svg>
      <svg v-else viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round">
        <rect x="6" y="9" width="36" height="30" rx="5" />
        <path d="m11 34 9-10 7 7 4-4 7 7" />
        <circle cx="32" cy="18" r="3" />
      </svg>
    </div>
    <p v-if="title" class="state__title">{{ title }}</p>
    <p v-if="desc" class="state__desc">{{ desc }}</p>
    <el-button v-if="type === 'error'" class="state__btn" @click="$emit('retry')">重试</el-button>
    <div v-if="$slots.default" class="state__actions"><slot /></div>
  </div>
</template>

<style scoped lang="scss">
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 72px lp.$space-4;
  color: var(--el-text-color-secondary);
}
.state--compact {
  padding: 28px lp.$space-4;
}
.state--error {
  color: var(--el-color-danger);
}
.state__title {
  margin: lp.$space-3 0 0;
  font-size: lp.$font-size-medium;
  color: var(--el-text-color-primary);
}
.state--notfound .state__title,
.state--empty .state__title {
  color: var(--el-text-color-secondary);
}
.state--error .state__title {
  color: var(--el-color-danger);
}
.state__desc {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
}
.state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
}
.state__btn,
.state__actions {
  margin-top: lp.$space-4;
}
// 手机上按钮不低于 44px
@include lp.mobile {
  .state__btn,
  .state__actions :deep(.el-button) {
    min-height: lp.$component-size-mobile;
    min-width: 120px;
  }
}
.spinner {
  display: inline-block;
  width: 28px;
  height: 28px;
  border: 2.5px solid var(--el-border-color-lighter);
  border-top-color: var(--el-text-color-secondary);
  border-radius: 50%;
  animation: lp-spin 0.8s linear infinite;
}
</style>
