<script setup lang="ts">
/**
 * 子用户页（regcode-subusers-spec.md 第 3–9 条）
 * - 顶行「已建 N / 最多 M 个」，超上限提醒色；建满后「新建子用户」禁用 + 灰字「已达上限，请联系管理员」
 * - 电脑表格；手机卡片（底部三个 44px 按钮）。停用的排最后、变灰、按钮换「启用」
 * - 停用先确认（N 用 quota 接口的 refundableTotal）；启用后直接打开额度窗口并提示
 * - 每次操作后刷新 /auth/me 和我的次数
 */
import { computed, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import regCodeApi from '@/api/regcode'
import type { RegCodeSubUserList } from '@/api/types'
import StateBlock from '@/components/StateBlock.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { confirmAction, showToast } from '@/utils/ui'
import { isExhausted, limitState, sortSubUsers, usageText } from '../regcode-quota.js'
import type { RegcodeData } from '../useRegcodeData'
import SubUserCreateDialog from './SubUserCreateDialog.vue'
import SubUserQuotaDialog from './SubUserQuotaDialog.vue'
import PasswordResultDialog from './PasswordResultDialog.vue'

type SubUser = RegCodeSubUserList['items'][number]

const props = defineProps<{ data: RegcodeData; list: RegCodeSubUserList | null; loading: boolean; error: string }>()
const emit = defineEmits<{ reload: [] }>()
const { isMobile } = useBreakpoint()

const users = computed(() => sortSubUsers(props.list?.items || []))
const limit = computed(() => limitState(props.list?.createdCount ?? 0, props.list?.maxSubUsers ?? 0))
const canCreate = computed(() => !!props.list && limit.value.canCreate && props.list.canCreate !== false)
const creatorUnlimited = computed(() => !!props.data.quota.value?.unlimited)

const createOpen = ref(false)
const quotaOpen = ref(false)
const quotaUser = ref<SubUser | null>(null)
const quotaNotice = ref('')
const pwdOpen = ref(false)
const pwd = ref({ username: '', password: '' })
const busyId = ref('')

const nameOf = (u: SubUser) => (u.nickname ? `${u.nickname}（${u.username}）` : u.username)
const dateOf = (t: string) => (t ? String(t).slice(0, 16).replace('T', ' ') : '—')

async function afterChange() {
  emit('reload')
  await props.data.refreshAfterChange()
}

function openCreate() {
  if (!canCreate.value) return
  createOpen.value = true
}
function openQuota(u: SubUser, notice = '') {
  quotaUser.value = u
  quotaNotice.value = notice
  quotaOpen.value = true
}

async function disable(u: SubUser) {
  busyId.value = u.id
  let refundable = Math.max(0, (u.allocatedTotal || 0) - (u.usedTotal || 0))
  try {
    const q = await regCodeApi.getSubUserQuota(u.id)
    if (q && Number.isFinite(Number(q.refundableTotal))) refundable = Number(q.refundableTotal)
  } catch {
    busyId.value = ''
    return
  }
  busyId.value = ''
  const ok = await confirmAction(`停用「${nameOf(u)}」？`, `停用后，他没用完的 ${refundable} 次会退回给你。停用后他不能再登录，之后可以再启用。`, {
    confirmText: '停用',
    danger: true,
  })
  if (!ok) return
  busyId.value = u.id
  try {
    const res = await regCodeApi.setSubUserStatus(u.id, 0)
    showToast(`已停用，退回 ${res?.refundedTotal ?? refundable} 次`, 'success')
    await afterChange()
  } catch {
    /* 请求层已提示 */
  } finally {
    busyId.value = ''
  }
}

async function enable(u: SubUser) {
  busyId.value = u.id
  try {
    await regCodeApi.setSubUserStatus(u.id, 1)
    await afterChange()
    openQuota({ ...u, status: 1, usedTotal: 0, allocatedTotal: 0 }, '已启用，额度为 0，请分配次数')
  } catch {
    /* 人数已满等请求层已提示 */
  } finally {
    busyId.value = ''
  }
}

async function resetPassword(u: SubUser) {
  const ok = await confirmAction(`重置「${nameOf(u)}」的密码？`, '系统会生成新密码，旧密码立即失效。', { confirmText: '重置' })
  if (!ok) return
  busyId.value = u.id
  try {
    const res = await regCodeApi.resetSubUserPassword(u.id)
    pwd.value = { username: u.username, password: res?.password || '' }
    pwdOpen.value = true
  } catch {
    /* 请求层已提示 */
  } finally {
    busyId.value = ''
  }
}
</script>

<template>
  <div class="sub">
    <div class="sub__head">
      <div class="sub__limit">
        <span :class="{ 'is-over': limit.over }">{{ list ? limit.text : '' }}</span>
        <small v-if="list && !canCreate" class="sub__full">已达上限，请联系管理员</small>
      </div>
      <el-button v-if="!isMobile && list" type="primary" :icon="Plus" :disabled="!canCreate" @click="openCreate">新建子用户</el-button>
    </div>

    <StateBlock v-if="loading && !list" type="loading" compact />
    <StateBlock v-else-if="error && !list" type="error" title="加载失败" :desc="error" @retry="emit('reload')" />
    <StateBlock v-else-if="list && !users.length" type="empty" title="还没有子用户" desc="子用户可以用你分给他的次数生成注册码">
      <el-button type="primary" :icon="Plus" :disabled="!canCreate" @click="openCreate">新建</el-button>
    </StateBlock>

    <!-- 电脑：表格 -->
    <div v-else-if="list && !isMobile" class="sub__table-wrap">
      <el-table :data="users" :row-class-name="({ row }) => (row.status === 1 ? '' : 'is-disabled')" class="sub__table">
        <el-table-column label="账号" prop="username" min-width="120">
          <template #default="{ row }">
            <strong class="sub__account">{{ row.username }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="昵称" prop="nickname" min-width="110">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" disable-transitions>{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已用/分配" width="100">
          <template #default="{ row }">
            <span :class="{ 'is-exhausted': row.status === 1 && isExhausted(row.usedTotal, row.allocatedTotal) }">
              {{ usageText(row.usedTotal, row.allocatedTotal) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ dateOf(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <el-button link type="primary" :disabled="busyId === row.id" @click="openQuota(row as SubUser)">额度</el-button>
              <el-button link type="primary" :disabled="busyId === row.id" @click="resetPassword(row as SubUser)">重置密码</el-button>
              <el-button link type="danger" :disabled="busyId === row.id" @click="disable(row as SubUser)">停用</el-button>
            </template>
            <el-button v-else link type="primary" :loading="busyId === row.id" @click="enable(row as SubUser)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 手机：卡片 -->
    <ul v-else-if="list" class="sub__cards">
      <li v-for="u in users" :key="u.id" class="sub-card" :class="{ 'is-disabled': u.status !== 1 }">
        <div class="sub-card__top">
          <strong class="sub-card__name">{{ u.username }}</strong>
          <el-tag :type="u.status === 1 ? 'success' : 'info'" disable-transitions>{{ u.status === 1 ? '启用' : '停用' }}</el-tag>
        </div>
        <p class="sub-card__meta">{{ u.nickname || '未填昵称' }} · {{ dateOf(u.createTime) }}</p>
        <p class="sub-card__usage">
          已用/分配
          <strong :class="{ 'is-exhausted': u.status === 1 && isExhausted(u.usedTotal, u.allocatedTotal) }">
            {{ usageText(u.usedTotal, u.allocatedTotal) }}
          </strong>
        </p>
        <div class="sub-card__actions">
          <template v-if="u.status === 1">
            <el-button :disabled="busyId === u.id" @click="openQuota(u)">额度</el-button>
            <el-button :disabled="busyId === u.id" @click="resetPassword(u)">重置密码</el-button>
            <el-button type="danger" plain :disabled="busyId === u.id" @click="disable(u)">停用</el-button>
          </template>
          <el-button v-else type="primary" plain :loading="busyId === u.id" @click="enable(u)">启用</el-button>
        </div>
      </li>
    </ul>

    <FixedActionBar v-if="isMobile && list && users.length">
      <el-button type="primary" :icon="Plus" :disabled="!canCreate" @click="openCreate">
        {{ canCreate ? '新建子用户' : '已达上限' }}
      </el-button>
    </FixedActionBar>

    <SubUserCreateDialog v-model="createOpen" :data="data" @created="afterChange" />
    <SubUserQuotaDialog
      v-model="quotaOpen"
      :sub-user="quotaUser"
      :notice="quotaNotice"
      :creator-unlimited="creatorUnlimited"
      @changed="afterChange"
    />
    <PasswordResultDialog v-model="pwdOpen" :username="pwd.username" :password="pwd.password" />
  </div>
</template>

<style scoped lang="scss">
.sub__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: lp.$space-3;
  min-height: 44px;
  margin-bottom: lp.$space-3;
}
.sub__limit {
  display: flex;
  flex-direction: column;
  color: var(--el-text-color-primary);
  font-weight: lp.$font-weight-medium;
  .is-over {
    color: var(--el-color-warning);
  }
}
.sub__full {
  font-size: lp.$font-size-extra-small;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}
.is-exhausted {
  color: var(--el-color-warning);
}
.sub__table-wrap {
  border-radius: lp.$radius-card;
  overflow: hidden;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.sub__table :deep(.is-disabled) td {
  color: var(--el-text-color-placeholder);
  .sub__account {
    color: var(--el-text-color-placeholder);
  }
}
.sub__account {
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.sub__cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
}
.sub-card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  &.is-disabled {
    .sub-card__name,
    .sub-card__meta,
    .sub-card__usage,
    .sub-card__usage strong {
      color: var(--el-text-color-placeholder);
    }
  }
}
.sub-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: lp.$space-2;
}
.sub-card__name {
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.sub-card__meta {
  margin: 4px 0 0;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.sub-card__usage {
  margin: lp.$space-2 0 0;
  color: var(--el-text-color-regular);
  strong {
    margin-left: lp.$space-1;
    font-weight: lp.$font-weight-semibold;
    color: var(--el-text-color-primary);
    &.is-exhausted {
      color: var(--el-color-warning);
    }
  }
}
.sub-card__actions {
  display: flex;
  gap: lp.$space-2;
  margin-top: lp.$space-3;
  :deep(.el-button) {
    flex: 1;
    height: lp.$component-size-mobile;
    margin-left: 0;
  }
}
</style>
