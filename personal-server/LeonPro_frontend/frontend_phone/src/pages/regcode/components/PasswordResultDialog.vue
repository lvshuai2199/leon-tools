<script setup lang="ts">
/** 重置密码结果：新密码只显示这一次，带「复制」 */
import { CopyDocument } from '@element-plus/icons-vue'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import { copyText } from '@/utils/ui'

const visible = defineModel<boolean>({ required: true })
defineProps<{ username: string; password: string }>()
</script>

<template>
  <ResponsiveDialog v-model="visible" title="新密码" :close-on-click-modal="false">
    <p class="prd__lead">「{{ username }}」的新密码：</p>
    <div class="prd__box">
      <code class="prd__pwd">{{ password }}</code>
      <el-button type="primary" plain :icon="CopyDocument" @click="copyText(password, '新密码已复制')">复制</el-button>
    </div>
    <el-alert type="warning" :closable="false" show-icon title="只显示这一次，关闭后无法再查看，请先复制发给对方。" />
    <template #footer>
      <el-button type="primary" @click="visible = false">我已记下</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped lang="scss">
.prd__lead {
  margin: 0 0 lp.$space-2;
  color: var(--el-text-color-regular);
}
.prd__box {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  margin-bottom: lp.$space-3;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
}
.prd__pwd {
  flex: 1;
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 20px;
  letter-spacing: 1px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
</style>
