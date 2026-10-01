<script setup lang="ts">
/** 手机上的确认框：从底部弹出的抽屉（由 utils/ui.ts 的 confirmAction 动态创建）。标题 18px/600 左对齐，按钮 44px */
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    content?: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
  }>(),
  { content: '', confirmText: '确定', cancelText: '取消', danger: false },
)
const emit = defineEmits<{ done: [ok: boolean] }>()

const visible = ref(true)
let result = false

function finish(ok: boolean) {
  result = ok
  visible.value = false
}
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="btt"
    size="auto"
    :with-header="false"
    class="lp-confirm-drawer"
    append-to-body
    @closed="emit('done', result)"
  >
    <div class="cd">
      <p class="cd__title">{{ props.title }}</p>
      <p v-if="props.content" class="cd__content">{{ props.content }}</p>
      <div class="cd__actions">
        <el-button size="large" @click="finish(false)">{{ props.cancelText }}</el-button>
        <el-button size="large" :type="props.danger ? 'danger' : 'primary'" @click="finish(true)">
          {{ props.confirmText }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.cd {
  padding: lp.$space-1 0 0;
  text-align: left;
}
.cd__title {
  margin: 0;
  font-size: lp.$font-size-large;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.cd__content {
  margin: lp.$space-2 0 0;
  font-size: lp.$font-size-mobile-body;
  color: var(--el-text-color-regular);
  white-space: pre-line;
}
.cd__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: lp.$space-3;
  margin-top: lp.$space-5;
  .el-button {
    height: lp.$component-size-mobile;
  }
  .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
