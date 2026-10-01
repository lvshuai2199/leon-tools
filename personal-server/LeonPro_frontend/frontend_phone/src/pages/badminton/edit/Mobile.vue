<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DocumentCopy, Picture } from '@element-plus/icons-vue'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import StateBlock from '@/components/StateBlock.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import BillForm from '../components/BillForm.vue'
import { useBadmintonForm } from '../components/useBadmintonForm'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { billTitle } from '@/utils/badminton-bill'

const route = useRoute()
const router = useRouter()
const id = computed(() => (route.name === 'badmintonNew' ? '' : String(route.params.id || '')))
const playDate = computed(() => (typeof route.query.date === 'string' ? route.query.date : ''))

const backTo = computed(() => ({
  path: '/badminton',
  query:
    typeof route.query.date === 'string' || typeof route.query.start === 'string' || typeof route.query.q === 'string'
      ? route.query
      : form.playDate
        ? { date: form.playDate }
        : {},
}))

const { form, loading, loadError, notFound, saving, dirty, load, save, remove, copyBill, exportSheet, addCourt, removeCourt, addBall, removeBall } =
  useBadmintonForm({
    onDeleted: () => leave(backTo.value),
  })
const { leave } = useLeaveGuard(() => dirty.value)

onMounted(() => load(id.value, playDate.value))

const title = computed(() => (form.id ? billTitle(form) : '新建球局'))

async function onSave() {
  const ok = await save()
  if (ok && !id.value && form.id) {
    await leave({ path: `/badminton/${encodeURIComponent(form.id)}`, query: backTo.value.query })
  }
}
</script>

<template>
  <div class="bd-edit">
    <PageBar :title="title" :back="backTo">
      <template #actions>
        <IconAction :icon="Picture" label="结算图" @click="exportSheet" />
        <IconAction :icon="DocumentCopy" label="复制账单" @click="copyBill" />
      </template>
    </PageBar>

    <StateBlock v-if="loading" type="loading" />
    <StateBlock v-else-if="notFound" type="notfound" title="球局不存在或没有权限查看">
      <el-button @click="router.replace('/badminton')">回到列表</el-button>
    </StateBlock>
    <StateBlock v-else-if="loadError" type="error" title="加载失败" :desc="loadError" @retry="load(id, playDate)" />
    <template v-else>
      <div class="bd-edit__card">
        <BillForm
          compact
          :form="form"
          @add-court="addCourt"
          @remove-court="removeCourt"
          @add-ball="addBall"
          @remove-ball="removeBall"
          @delete="remove"
        />
      </div>
      <FixedActionBar>
        <el-button @click="exportSheet">结算图</el-button>
        <el-button @click="copyBill">复制账单</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </FixedActionBar>
    </template>
  </div>
</template>

<style scoped lang="scss">
.bd-edit {
  padding: lp.$page-padding-mobile;
}
.bd-edit__card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
</style>
