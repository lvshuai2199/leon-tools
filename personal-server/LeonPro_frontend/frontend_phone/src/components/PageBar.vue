<script setup lang="ts">
/**
 * 页面标题栏：
 * - 手机：标题、返回写进布局的 44px 顶栏；右侧操作（#actions）传送到顶栏右侧的 44×44 操作区
 * - 电脑：在内容区顶部显示「← 标题 …… 操作」一行
 * 插槽 #actions 带 { mobile }，手机上建议放图标按钮（IconAction），电脑上放文字按钮。
 */
import { onBeforeUnmount, watchEffect } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { clearPageChrome, setPageChrome } from '@/composables/usePageChrome'

const props = withDefaults(
  defineProps<{
    title: string
    /** 返回去哪；不填按浏览历史返回 */
    back?: RouteLocationRaw | null
    showBack?: boolean
    /** 电脑上标题下方的说明 */
    desc?: string
  }>(),
  { back: null, showBack: true, desc: '' },
)

const router = useRouter()
const { isMobile } = useBreakpoint()
const owner = Symbol('page-bar')

watchEffect(() => {
  setPageChrome(owner, { title: props.title, back: props.back, showBack: props.showBack })
})
onBeforeUnmount(() => clearPageChrome(owner))

function goBack() {
  if (props.back) router.replace(props.back)
  else if (window.history.state?.back) router.back()
  else router.replace('/')
}
</script>

<template>
  <Teleport v-if="isMobile" to="#lp-navbar-actions" defer>
    <slot name="actions" :mobile="true" />
  </Teleport>
  <header v-else class="pagebar">
    <button v-if="showBack" type="button" class="pagebar__back" aria-label="返回" @click="goBack">
      <el-icon :size="20"><ArrowLeft /></el-icon>
    </button>
    <div class="pagebar__text">
      <h1 class="pagebar__title">{{ title }}</h1>
      <p v-if="desc" class="pagebar__desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.actions" class="pagebar__actions">
      <slot name="actions" :mobile="false" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.pagebar {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  padding: lp.$space-5 0 lp.$space-4;
}
.pagebar__back {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-left: -(lp.$space-2);
  padding: 0;
  border: none;
  border-radius: lp.$radius-base;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;
  &:hover {
    background: var(--el-fill-color-light);
  }
}
.pagebar__text {
  flex: 1;
  min-width: 0;
}
.pagebar__title {
  margin: 0;
  font-size: lp.$font-size-extra-large;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.pagebar__desc {
  margin: 2px 0 0;
  font-size: lp.$font-size-base;
  color: var(--el-text-color-secondary);
}
.pagebar__actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
