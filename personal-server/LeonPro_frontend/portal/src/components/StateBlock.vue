<script setup lang="ts">
/**
 * 通用状态块
 * - empty：空状态（灰色图片图标）
 * - notfound：不存在 / 未公开（中性灰色图标 #8A8F98，无重试；操作按钮通过默认插槽传入）
 * - error：加载失败（红色感叹号 + 重试按钮）
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
    <div class="state__icon" aria-hidden="true">
      <span v-if="type === 'loading'" class="spinner" />
      <svg v-else-if="type === 'notfound'" viewBox="0 0 48 48" width="44" height="44">
        <circle cx="21" cy="21" r="13" fill="none" stroke="currentColor" stroke-width="2.5" />
        <path d="M30.5 30.5L40 40" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
        <path d="M16 21h10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
      </svg>
      <svg v-else-if="type === 'error'" viewBox="0 0 48 48" width="44" height="44">
        <circle cx="24" cy="24" r="20" fill="none" stroke="currentColor" stroke-width="2.5" />
        <path d="M24 14v13" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
        <circle cx="24" cy="33.5" r="2" fill="currentColor" />
      </svg>
      <svg v-else viewBox="0 0 64 48" width="60" height="45">
        <rect x="4" y="6" width="56" height="36" rx="6" fill="none" stroke="currentColor" stroke-width="2.5" />
        <path d="M12 36l12-13 9 9 6-5 13 9" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linejoin="round" />
        <circle cx="44" cy="17" r="4" fill="none" stroke="currentColor" stroke-width="2.5" />
      </svg>
    </div>
    <p v-if="title" class="state__title">{{ title }}</p>
    <p v-if="desc" class="state__desc">{{ desc }}</p>
    <button v-if="type === 'error'" class="btn state__btn" type="button" @click="$emit('retry')">重试</button>
    <slot />
  </div>
</template>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 72px 16px;
  color: var(--text-3);
}
.state--compact {
  padding: 28px 16px;
}
.state--error {
  color: #d70015;
}
.state--notfound {
  color: #8a8f98;
}
.state__title {
  margin: 14px 0 0;
  font-size: 15px;
  color: var(--text);
}
.state__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-2);
}
.state__btn {
  margin-top: 16px;
}
.spinner {
  display: inline-block;
  width: 28px;
  height: 28px;
  border: 2.5px solid rgba(0, 0, 0, 0.12);
  border-top-color: var(--text-2);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
