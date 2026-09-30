<script setup lang="ts">
/**
 * 已付款 / 已发货 的可点标签：开 = 成功色浅底 + 勾，关 = 灰色（未付款 / 未发货）。
 * 标签高：手机 28、电脑 24（圆角 4），点击区域上下各扩 8。readonly 时只显示（分享页）。
 * size="large" 是编辑表单里的状态选项：手机 44、电脑 32（和按钮一样高）。
 */
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{ field: 'paid' | 'shipped'; on: boolean | number | undefined; readonly?: boolean; size?: 'default' | 'large' }>(),
  { readonly: false, size: 'default' },
)
defineEmits<{ toggle: [] }>()

const text = computed(() => {
  if (props.field === 'paid') return props.on ? '已付款' : '未付款'
  return props.on ? '已发货' : '未发货'
})
const label = computed(() => {
  const next = props.field === 'paid' ? (props.on ? '未付款' : '已付款') : props.on ? '未发货' : '已发货'
  return `${text.value}，点击改为${next}`
})
</script>

<template>
  <span v-if="readonly" class="cst" :class="[{ 'is-on': on }, `cst--${size}`]">
    <el-icon v-if="on" class="cst__icon"><Check /></el-icon>{{ text }}
  </span>
  <button
    v-else
    type="button"
    class="cst cst--btn"
    :class="[{ 'is-on': on }, `cst--${size}`]"
    :aria-pressed="!!on"
    :aria-label="label"
    @click.stop="$emit('toggle')"
  >
    <el-icon v-if="on" class="cst__icon"><Check /></el-icon>{{ text }}
  </button>
</template>

<style scoped lang="scss">
.cst {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 24px;
  padding: 0 lp.$space-2;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-small;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-extra-small;
  line-height: 1;
  white-space: nowrap;
  &.is-on {
    border-color: transparent;
    background: var(--lp-color-success-bg);
    color: var(--el-color-success);
    font-weight: lp.$font-weight-medium;
  }
}
@include lp.mobile {
  .cst {
    height: 28px;
  }
}
.cst--large {
  height: 32px;
  padding: 0 lp.$space-3;
  font-size: lp.$font-size-base;
  border-radius: lp.$radius-base;
  @include lp.mobile {
    height: lp.$component-size-mobile;
    padding: 0 lp.$space-4;
    font-size: lp.$font-size-mobile-body;
  }
}
.cst--btn {
  cursor: pointer;
  font-family: inherit;
  // 点击区域上下扩到 44px
  &::after {
    content: '';
    position: absolute;
    left: -2px;
    right: -2px;
    top: -8px;
    bottom: -8px;
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
.cst__icon {
  font-size: 12px;
}
</style>
