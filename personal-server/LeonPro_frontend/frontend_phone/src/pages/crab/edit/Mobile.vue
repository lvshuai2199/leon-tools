<script setup lang="ts">
/** 手机：蟹单详情页。删除在表单底部（红色文字），保存/分享固定在底部（不显示底部标签栏）；有没保存的修改时离开先确认 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MoreFilled, Link, Tickets } from '@element-plus/icons-vue'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import StateBlock from '@/components/StateBlock.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import ActionSheet, { type SheetAction } from '@/components/ActionSheet.vue'
import CrabForm from '../components/CrabForm.vue'
import { useCrabForm } from '../components/useCrabForm'
import { useLeaveGuard } from '@/composables/useLeaveGuard'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id || ''))

const backTo = computed(() => ({
  path: '/crab',
  query: typeof route.query.date === 'string' || typeof route.query.start === 'string' ? route.query : { date: form.shipDate },
}))

const { form, loading, loadError, notFound, saving, dirty, load, save, remove, share, copyLink, exportSheet } = useCrabForm({
  onDeleted: () => leave(backTo.value),
})
const { leave } = useLeaveGuard(() => dirty.value)

onMounted(() => load(id.value))

const moreOpen = ref(false)
const moreActions = computed<SheetAction[]>(() => [
  { key: 'sheet', label: '生成发货图', icon: Tickets },
  { key: 'link', label: '复制分享链接', icon: Link, disabled: !form.publicId },
])
function onMore(key: string) {
  if (key === 'sheet') exportSheet()
  else if (key === 'link') copyLink()
}
</script>

<template>
  <div class="crab-edit">
    <PageBar title="出货单详情" :back="backTo">
      <template #actions>
        <IconAction v-if="form.id" :icon="MoreFilled" label="更多" @click="moreOpen = true" />
      </template>
    </PageBar>

    <StateBlock v-if="loading" type="loading" />
    <StateBlock v-else-if="notFound" type="notfound" title="出货单不存在或没有权限查看">
      <el-button @click="router.replace('/crab')">回到列表</el-button>
    </StateBlock>
    <StateBlock v-else-if="loadError" type="error" title="加载失败" :desc="loadError" @retry="load(id)" />
    <template v-else>
      <div class="crab-edit__card">
        <CrabForm :form="form" @delete="remove" />
      </div>
      <FixedActionBar>
        <el-button type="primary" plain :disabled="!form.publicId" @click="share">分享</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </FixedActionBar>
    </template>

    <ActionSheet v-model="moreOpen" :actions="moreActions" @select="onMore" />
  </div>
</template>

<style scoped lang="scss">
.crab-edit {
  padding: lp.$page-padding-mobile;
}
.crab-edit__card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
</style>
