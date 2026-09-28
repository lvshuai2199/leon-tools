<script setup lang="ts">
/**
 * 额度：按配置列出已用、分配，输入框追加（从我的剩余里扣）或收回（最多收回到已用数量，退回给我）。
 * 电脑 480 弹窗，手机底部抽屉。启用后打开时顶部提示「已启用，额度为 0，请分配次数」。
 */
import { computed, reactive, ref, watch } from 'vue'
import regCodeApi from '@/api/regcode'
import type { RegCodeSubUserQuota } from '@/api/types'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import StateBlock from '@/components/StateBlock.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { showToast } from '@/utils/ui'
import { adjustRows, buildDeltas, maxAdd, maxRevoke, type AdjustMode } from '../regcode-quota.js'

const visible = defineModel<boolean>({ required: true })
const props = withDefaults(
  defineProps<{ subUser: { id: string; username: string; nickname?: string } | null; notice?: string; creatorUnlimited?: boolean }>(),
  { notice: '', creatorUnlimited: false },
)
const emit = defineEmits<{ changed: [] }>()
const { isMobile } = useBreakpoint()

const loading = ref(false)
const loadError = ref('')
const quota = ref<RegCodeSubUserQuota | null>(null)
const mode = ref<AdjustMode>('add')
const values = reactive<Record<string, number | undefined>>({})
const saving = ref(false)
const error = ref('')

const rows = computed(() => adjustRows(quota.value, props.creatorUnlimited))
const modes = [
  { label: '追加', value: 'add' },
  { label: '收回', value: 'revoke' },
]
const title = computed(() => (props.subUser ? `额度 · ${props.subUser.nickname || props.subUser.username}` : '额度'))

function resetValues() {
  for (const k of Object.keys(values)) delete values[k]
  for (const r of rows.value) values[r.configId] = 0
  error.value = ''
}

async function load() {
  if (!props.subUser) return
  loading.value = true
  loadError.value = ''
  try {
    quota.value = await regCodeApi.getSubUserQuota(props.subUser.id)
    resetValues()
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => {
  if (v) {
    mode.value = 'add'
    quota.value = null
    load()
  }
})
watch(mode, resetValues)

function maxOf(r: (typeof rows.value)[number]) {
  if (mode.value === 'add') return maxAdd(r) ?? 99999
  return maxRevoke(r)
}
function hintOf(r: (typeof rows.value)[number]) {
  if (mode.value === 'add') {
    const m = maxAdd(r)
    return m == null ? '你的次数不限' : `你还剩 ${m} 次`
  }
  return `最多收回 ${maxRevoke(r)} 次`
}

async function submit() {
  if (!props.subUser) return
  const { items, error: err } = buildDeltas(rows.value, mode.value, values)
  error.value = err
  if (err) return
  saving.value = true
  try {
    quota.value = await regCodeApi.adjustSubUserQuota(props.subUser.id, items)
    const total = items.reduce((n, i) => n + Math.abs(i.delta), 0)
    showToast(mode.value === 'add' ? `已追加 ${total} 次` : `已收回 ${total} 次，退回给你`, 'success')
    emit('changed')
    visible.value = false
  } catch {
    /* 次数不足等请求层已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ResponsiveDialog v-model="visible" :title="title">
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="false" class="suq__notice" />
    <StateBlock v-if="loading" type="loading" compact />
    <StateBlock v-else-if="loadError" type="error" compact title="加载失败" :desc="loadError" @retry="load" />
    <template v-else-if="quota">
      <el-segmented v-model="mode" :options="modes" block class="suq__mode" />
      <p class="suq__hint">
        {{ mode === 'add' ? '追加的次数从你的剩余次数里扣除' : '只能收回还没用的次数，收回的退回给你' }}
      </p>
      <p v-if="!rows.length" class="suq__none">没有可调整的配置</p>
      <div v-for="r in rows" :key="r.configId" class="suq__row">
        <div class="suq__name">
          <span>{{ r.configName }}</span>
          <small>已用 {{ r.used }} · 分配 {{ r.allocated }} · {{ hintOf(r) }}</small>
        </div>
        <el-input-number
          v-model="values[r.configId]"
          :min="0"
          :max="maxOf(r)"
          :disabled="maxOf(r) === 0"
          :size="isMobile ? 'large' : 'default'"
          :aria-label="`${r.configName} ${mode === 'add' ? '追加' : '收回'}次数`"
          class="suq__num"
        />
      </div>
      <p v-if="error" class="suq__error" role="alert">{{ error }}</p>
    </template>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!quota" @click="submit">
        {{ mode === 'add' ? '确认追加' : '确认收回' }}
      </el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped lang="scss">
.suq__notice {
  margin-bottom: lp.$space-3;
}
.suq__mode {
  margin-bottom: lp.$space-2;
}
.suq__hint,
.suq__none {
  margin: 0 0 lp.$space-2;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.suq__row {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  padding: lp.$space-2 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &:last-of-type {
    border-bottom: none;
  }
}
.suq__name {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  color: var(--el-text-color-primary);
  small {
    font-size: lp.$font-size-extra-small;
    color: var(--el-text-color-secondary);
  }
}
.suq__num {
  flex: none;
  width: 140px;
}
.suq__error {
  margin: lp.$space-2 0 0;
  color: var(--el-color-danger);
}
</style>
