<script setup lang="ts">
/**
 * 通用状态块（沿用原展示端 portal 的样式，颜色换成主题变量）。图标统一放在 48×48 的框里、线宽 2、次要文字色。
 * - empty：空状态。icon 选图标，不用图片图标（去掉拍照以后图片图标容易被当成「上传图片」）：
 *   inbox 默认（空托盘）、document 文档（还没有识别结果）、list 列表（出货单列表为空）、users 人（还没有子用户）、grid 宫格（没有可用工具）
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
    icon?: 'inbox' | 'document' | 'list' | 'users' | 'grid'
  }>(),
  { type: 'empty', compact: false, icon: 'inbox' },
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
      <svg v-else-if="icon === 'document'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M13 6h15l9 9v25a2 2 0 0 1-2 2H13a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z" />
        <path d="M28 6v9h9" />
        <path d="M17 24h14M17 30h14M17 36h8" />
      </svg>
      <svg v-else-if="icon === 'list'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="7" y="7" width="34" height="34" rx="5" />
        <path d="M20 17h14M20 24h14M20 31h14" />
        <circle cx="14.5" cy="17" r="1.5" fill="currentColor" stroke="none" />
        <circle cx="14.5" cy="24" r="1.5" fill="currentColor" stroke="none" />
        <circle cx="14.5" cy="31" r="1.5" fill="currentColor" stroke="none" />
      </svg>
      <svg v-else-if="icon === 'users'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="19" cy="17" r="7" />
        <path d="M6 39c0-7.2 5.8-12 13-12s13 4.8 13 12" />
        <path d="M31 10.5a7 7 0 0 1 0 13" />
        <path d="M35 28c4.3 1.6 7 5.4 7 11" />
      </svg>
      <svg v-else-if="icon === 'grid'" viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round">
        <rect x="8" y="8" width="13" height="13" rx="3" />
        <rect x="27" y="8" width="13" height="13" rx="3" />
        <rect x="8" y="27" width="13" height="13" rx="3" />
        <rect x="27" y="27" width="13" height="13" rx="3" />
      </svg>
      <svg v-else viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6 27 12 11a3 3 0 0 1 2.8-2h18.4a3 3 0 0 1 2.8 2L42 27v10a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V27Z" />
        <path d="M6 27h10l3 5h10l3-5h10" />
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
