<script setup lang="ts">
/**
 * 弹窗：电脑 480 宽居中对话框，手机底部抽屉（顶部两角 12px 圆角）。
 * 插槽：默认（内容）、footer（按钮；手机上按钮等分、44px）。
 * beforeClose：点关闭、点遮罩、按 Esc 时先调用（例如有没保存的修改时先确认），调用 done() 才真正关闭。
 */
import { useBreakpoint } from '@/composables/useBreakpoint'

const visible = defineModel<boolean>({ required: true })
withDefaults(
  defineProps<{ title: string; width?: string; closeOnClickModal?: boolean; beforeClose?: (done: () => void) => void }>(),
  {
    width: 'var(--lp-dialog-width-desktop)',
    closeOnClickModal: true,
    beforeClose: undefined,
  },
)
const emit = defineEmits<{ closed: []; open: [] }>()
const { isMobile } = useBreakpoint()
</script>

<template>
  <el-drawer
    v-if="isMobile"
    v-model="visible"
    direction="btt"
    size="auto"
    :title="title"
    class="lp-rdialog lp-rdialog--sheet"
    append-to-body
    :close-on-click-modal="closeOnClickModal"
    :before-close="beforeClose"
    @open="emit('open')"
    @closed="emit('closed')"
  >
    <div class="rd-body"><slot /></div>
    <template v-if="$slots.footer" #footer>
      <div class="rd-footer rd-footer--sheet"><slot name="footer" /></div>
    </template>
  </el-drawer>
  <el-dialog
    v-else
    v-model="visible"
    :title="title"
    :width="width"
    align-center
    class="lp-rdialog"
    append-to-body
    :close-on-click-modal="closeOnClickModal"
    :before-close="beforeClose"
    @open="emit('open')"
    @closed="emit('closed')"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <div class="rd-footer"><slot name="footer" /></div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.rd-body {
  max-height: 70vh;
  max-height: 70dvh;
  overflow-y: auto;
}
.rd-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: lp.$space-3;
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
.rd-footer--sheet {
  :deep(.el-button) {
    flex: 1;
    height: lp.$component-size-mobile;
  }
}
</style>
