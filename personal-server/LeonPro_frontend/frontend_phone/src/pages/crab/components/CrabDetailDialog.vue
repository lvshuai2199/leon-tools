<script setup lang="ts">
/**
 * 电脑版蟹单详情：480 宽弹窗，内容同手机详情页（CrabForm）。
 * 有没保存的修改时，点关闭 / 点遮罩 / 按 Esc 先确认「有未保存的内容，确定关闭？」（继续编辑 / 放弃修改）。
 */
import { computed, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import StateBlock from '@/components/StateBlock.vue'
import CrabForm from './CrabForm.vue'
import { useCrabForm } from './useCrabForm'

const props = defineProps<{ id: string | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const visible = computed({
  get: () => !!props.id,
  set: (v) => {
    if (!v) emit('close')
  },
})

const { form, loading, loadError, notFound, saving, dirty, load, save, remove, share, exportSheet } = useCrabForm({
  onDeleted: () => {
    emit('changed')
    emit('close')
  },
  onSaved: () => emit('changed'),
})

watch(
  () => props.id,
  (id) => {
    if (id) load(id)
  },
  { immediate: true },
)

const title = computed(() => (form.customerName ? `出货单 · ${form.customerName}` : '出货单'))

async function beforeClose(done: () => void) {
  if (!dirty.value) return done()
  const ok = await ElMessageBox.confirm('有未保存的内容，确定关闭？', '提示', {
    confirmButtonText: '放弃修改',
    cancelButtonText: '继续编辑',
    confirmButtonType: 'danger',
    type: 'warning',
    customStyle: { width: 'var(--lp-dialog-width-desktop)', maxWidth: '92vw' },
    autofocus: false,
  })
    .then(() => true)
    .catch(() => false)
  if (ok) done()
}
</script>

<template>
  <ResponsiveDialog v-model="visible" :title="title" :before-close="beforeClose">
    <StateBlock v-if="loading" type="loading" compact />
    <StateBlock v-else-if="notFound" type="notfound" compact title="出货单不存在或没有权限查看" />
    <StateBlock v-else-if="loadError" type="error" compact title="加载失败" :desc="loadError" @retry="id && load(id)" />
    <CrabForm v-else :form="form" :show-delete="false" />
    <template v-if="!loading && !notFound && !loadError" #footer>
      <el-button type="danger" text class="dlg-delete" @click="remove">删除</el-button>
      <el-button type="primary" plain @click="exportSheet">发货图</el-button>
      <el-button type="primary" plain :disabled="!form.publicId" @click="share">分享</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped lang="scss">
/* 文字按钮左内边距 15：往左挪同样的距离，文字和上面的字段左边对齐 */
.dlg-delete {
  margin-right: auto;
  margin-left: -15px;
}
</style>
