<script setup lang="ts">
/** 电脑版蟹单详情：480 宽弹窗，内容同手机详情页（CrabForm） */
import { computed, watch } from 'vue'
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

const { form, loading, loadError, notFound, saving, load, save, remove, scan, pickImage, share, exportSheet } = useCrabForm({
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
</script>

<template>
  <ResponsiveDialog v-model="visible" :title="title">
    <StateBlock v-if="loading" type="loading" compact />
    <StateBlock v-else-if="notFound" type="notfound" compact title="出货单不存在或没有权限查看" />
    <StateBlock v-else-if="loadError" type="error" compact title="加载失败" :desc="loadError" @retry="id && load(id)" />
    <CrabForm v-else :form="form" :show-delete="false" show-image @scan="scan" @pick-image="pickImage" />
    <template v-if="!loading && !notFound && !loadError" #footer>
      <el-button type="danger" text class="dlg-delete" @click="remove">删除</el-button>
      <el-button type="primary" plain @click="exportSheet">发货图</el-button>
      <el-button type="primary" plain :disabled="!form.publicId" @click="share">分享</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped lang="scss">
.dlg-delete {
  margin-right: auto;
}
</style>
