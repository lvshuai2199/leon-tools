<script setup lang="ts">
/**
 * 注册码生成（/regcode）：标题「注册码生成」+ 返回；标签页「生成」「子用户」，默认「生成」。
 * 子用户登录、不能管理子用户、上限 0 且一个都没建过（含停用的）时不显示标签页，只有生成。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import regCodeApi from '@/api/regcode'
import type { RegCodeSubUserList } from '@/api/types'
import PageBar from '@/components/PageBar.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { userStore } from '@/stores/user'
import { needsSubUserListCheck, showSubUserTab } from './regcode-quota.js'
import { useRegcodeData } from './useRegcodeData'
import GeneratePanel from './components/GeneratePanel.vue'
import SubUsersPanel from './components/SubUsersPanel.vue'

type Tab = 'generate' | 'subusers'

const route = useRoute()
const router = useRouter()
const { isMobile } = useBreakpoint()
const data = useRegcodeData()

const tab = ref<Tab>(route.query.tab === 'subusers' ? 'subusers' : 'generate')
const subList = ref<RegCodeSubUserList | null>(null)
const subLoading = ref(false)
const subError = ref('')
/** 上限 0 时要看列表才知道显不显示；查完之前先不显示 */
const listChecked = ref(false)

const regCode = computed(() => userStore.regCode.value)
const showTabs = computed(() => showSubUserTab(regCode.value, listChecked.value ? (subList.value?.items.length ?? 0) : null))
/** 实际显示的页：没有「子用户」页（如子用户登录、地址里带着 ?tab=subusers）时一律显示生成页 */
const activeTab = computed<Tab>(() => (showTabs.value ? tab.value : 'generate'))
/** 能管理子用户才去查子用户列表（子用户账号查了只会 403） */
const canListSubUsers = computed(() => showSubUserTab(regCode.value, 1))

async function loadSubUsers(silent = false) {
  subLoading.value = true
  subError.value = ''
  try {
    subList.value = await regCodeApi.listSubUsers({ silent })
  } catch (e) {
    subError.value = (e as Error)?.message || '加载失败'
  } finally {
    subLoading.value = false
    listChecked.value = true
  }
}

function setTab(v: Tab) {
  tab.value = v
  router.replace({ query: { ...route.query, tab: v === 'generate' ? undefined : v } })
}

watch(tab, (v) => {
  if (v === 'subusers' && canListSubUsers.value && !subList.value && !subLoading.value) loadSubUsers()
})
watch(showTabs, (v) => {
  if (!v && tab.value !== 'generate') tab.value = 'generate'
})

onMounted(async () => {
  data.loadConfigs()
  data.loadQuota()
  await userStore.loadMe({ silent: true }).catch(() => false)
  if ((tab.value === 'subusers' && canListSubUsers.value) || needsSubUserListCheck(regCode.value)) loadSubUsers(true)
})
</script>

<template>
  <div class="regcode">
    <PageBar title="注册码生成" back="/" />
    <div v-if="showTabs" class="regcode__tabs" :class="{ 'is-mobile': isMobile }">
      <el-tabs :model-value="activeTab" @update:model-value="(v: string | number) => setTab(v as Tab)">
        <el-tab-pane label="生成" name="generate" />
        <el-tab-pane label="子用户" name="subusers" />
      </el-tabs>
    </div>
    <GeneratePanel v-show="activeTab === 'generate'" :data="data" />
    <SubUsersPanel
      v-if="activeTab === 'subusers'"
      :data="data"
      :list="subList"
      :loading="subLoading"
      :error="subError"
      @reload="loadSubUsers(true)"
    />
  </div>
</template>

<style scoped lang="scss">
.regcode {
  @include lp.mobile {
    padding: lp.$page-padding-mobile;
  }
}
.regcode__tabs {
  margin-bottom: lp.$space-3;
  :deep(.el-tabs__header) {
    margin: 0;
  }
  &.is-mobile {
    position: sticky;
    top: var(--topbar-h, 44px);
    z-index: 5;
    margin: calc(-1 * #{lp.$page-padding-mobile}) calc(-1 * #{lp.$page-padding-mobile}) lp.$space-3;
    padding: 0 lp.$page-padding-mobile;
    background: var(--el-bg-color);
    :deep(.el-tabs__nav) {
      width: 100%;
    }
    :deep(.el-tabs__item) {
      flex: 1;
      height: 44px;
      font-size: lp.$font-size-mobile-body;
    }
  }
}
</style>
