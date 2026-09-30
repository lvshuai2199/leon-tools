<script setup lang="ts">
/**
 * 生成页：公司 → 配置 → 6 位注册码 → 生成。剩余按配置写「剩余 N 次」。
 * 结果区左边时长、右边注册码 +「复制」，生成前灰字「生成后显示」。手机上「重置」「生成」固定在底部。
 */
import { computed, ref, watch } from 'vue'
import regCodeApi from '@/api/regcode'
import SheetSelect from '@/components/SheetSelect.vue'
import StateBlock from '@/components/StateBlock.vue'
import FixedActionBar from '@/components/FixedActionBar.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { userStore } from '@/stores/user'
import { copyText, showToast } from '@/utils/ui'
import { remainingForConfig } from '../regcode-quota.js'
import type { RegcodeData } from '../useRegcodeData'

const props = defineProps<{ data: RegcodeData }>()
const { configs, configsLoading, configsError, quota } = props.data
const { isMobile } = useBreakpoint()

const FIELDS = [
  { key: 'oneMonthValid', label: '一个月' },
  { key: 'twoMonthValid', label: '两个月' },
  { key: 'fourMonthValid', label: '四个月' },
  { key: 'sixMonthValid', label: '六个月' },
  { key: 'thirteenMonthValid', label: '十三个月' },
  { key: 'longTimeValid', label: '永久' },
] as const

const company = ref('')
const configId = ref('')
const regCode = ref('')
const generating = ref(false)
const result = ref<Record<string, string> | null>(null)

const companyOptions = computed(() => {
  const names: string[] = []
  for (const c of configs.value) {
    const n = c.company || '未分组'
    if (!names.includes(n)) names.push(n)
  }
  return names.map((n) => ({ label: n, value: n }))
})
const configOptions = computed(() =>
  configs.value
    .filter((c) => (c.company || '未分组') === company.value)
    .map((c) => {
      const left = remainingForConfig(quota.value, c.id)
      return { label: c.name || c.componentName || c.id, value: c.id, desc: left == null ? '不限次数' : `剩余 ${left} 次` }
    }),
)
const current = computed(() => configs.value.find((c) => c.id === configId.value) || null)
const remaining = computed(() => (current.value ? remainingForConfig(quota.value, current.value.id) : null))
const quotaText = computed(() => {
  if (!quota.value) return ''
  return remaining.value == null ? '不限次数' : `剩余 ${remaining.value} 次`
})
const exhausted = computed(() => remaining.value === 0)
/** 次数用完时提示块下面的灰字：子用户找创建者，客户找管理员 */
const exhaustedHint = computed(() =>
  userStore.regCode.value?.isSubUser ? '次数已用完，请联系创建者分配' : '次数已用完，请联系管理员分配',
)

// 配置加载完默认选第一个公司、第一个配置
watch(
  configs,
  (list) => {
    if (!list.length) return
    if (!list.some((c) => c.id === configId.value)) {
      company.value = list[0].company || '未分组'
      configId.value = list[0].id
    }
  },
  { immediate: true },
)

function onCompany() {
  configId.value = configOptions.value[0]?.value || ''
  result.value = null
}
function onConfig() {
  result.value = null
}
function reset() {
  regCode.value = ''
  result.value = null
}

async function generate() {
  if (!current.value) {
    showToast('请选择配置', 'warning')
    return
  }
  const code = regCode.value.trim()
  if (code.length !== 6) {
    showToast('注册码需要 6 位', 'warning')
    return
  }
  if (exhausted.value) {
    showToast('这个配置的次数已用完', 'warning')
    return
  }
  generating.value = true
  try {
    const data = await regCodeApi.genTempRegCode({
      regCode: code,
      configId: current.value.id,
      company: current.value.company,
      applyName: current.value.name,
    })
    result.value = data || {}
    showToast('生成成功', 'success')
    props.data.loadQuota()
  } catch {
    /* 业务错误（次数不足等）请求层已提示 */
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <StateBlock v-if="configsLoading" type="loading" compact />
  <StateBlock v-else-if="configsError" type="error" title="加载失败" :desc="configsError" @retry="data.loadConfigs()" />
  <StateBlock v-else-if="!configs.length" type="empty" title="暂无可用配置" desc="还没有给你分配注册码配置，请联系管理员" />
  <div v-else class="gen">
    <section class="gen__card">
      <el-form label-position="top" class="gen__form" @submit.prevent="generate">
        <el-form-item label="公司">
          <SheetSelect v-model="company" :options="companyOptions" title="选择公司" @change="onCompany" />
        </el-form-item>
        <el-form-item label="配置">
          <SheetSelect v-model="configId" :options="configOptions" title="选择配置" @change="onConfig" />
        </el-form-item>
        <div v-if="quotaText" class="gen__quota" :class="{ 'is-empty': exhausted, 'has-hint': exhausted }">
          <span>当前配置</span>
          <strong>{{ quotaText }}</strong>
        </div>
        <p v-if="quotaText && exhausted" class="gen__quota-hint">{{ exhaustedHint }}</p>
        <el-form-item label="注册码">
          <el-input
            v-model="regCode"
            maxlength="6"
            placeholder="输入客户提供的 6 位注册码"
            autocomplete="off"
            class="gen__code"
            @keyup.enter="generate"
          />
        </el-form-item>
      </el-form>
      <FixedActionBar v-if="!isMobile">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" :loading="generating" :disabled="exhausted" @click="generate">生成</el-button>
      </FixedActionBar>
    </section>

    <section class="gen__card">
      <h2 class="gen__title">生成结果</h2>
      <ul class="gen__result">
        <li v-for="f in FIELDS" :key="f.key" class="gen__row">
          <span class="gen__label">{{ f.label }}</span>
          <template v-if="result && result[f.key]">
            <span class="gen__value">{{ result[f.key] }}</span>
            <el-button link type="primary" class="gen__copy" @click="copyText(result[f.key])">复制</el-button>
          </template>
          <span v-else class="gen__value is-placeholder">生成后显示</span>
        </li>
      </ul>
    </section>

    <FixedActionBar v-if="isMobile">
      <el-button @click="reset">重置</el-button>
      <el-button type="primary" :loading="generating" :disabled="exhausted" @click="generate">生成</el-button>
    </FixedActionBar>
  </div>
</template>

<style scoped lang="scss">
.gen {
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
  @media (min-width: 768px) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
    gap: lp.$space-4;
  }
}
.gen__card {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  @media (min-width: 768px) {
    padding: lp.$space-5;
  }
  :deep(.fab-inline) {
    margin-top: lp.$space-2;
  }
}
.gen__form :deep(.el-form-item) {
  margin-bottom: lp.$space-4;
}
.gen__quota {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: lp.$space-4;
  padding: lp.$space-2 lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-color-primary-light-9);
  color: var(--el-text-color-regular);
  strong {
    color: var(--el-color-primary);
    font-weight: lp.$font-weight-semibold;
  }
  &.is-empty {
    background: var(--lp-color-warning-bg);
    strong {
      color: var(--el-color-warning);
    }
  }
}
.gen__quota.has-hint {
  margin-bottom: lp.$space-1;
}
.gen__quota-hint {
  margin: 0 0 lp.$space-4;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.gen__code :deep(input) {
  letter-spacing: 2px;
  /* 只让输入的注册码有字间距，占位字正常 */
  &::placeholder {
    letter-spacing: 0;
  }
}
.gen__title {
  margin: 0 0 lp.$space-2;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.gen__result {
  list-style: none;
  margin: 0;
  padding: 0;
}
.gen__row {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  min-height: lp.$component-size-mobile;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &:last-child {
    border-bottom: none;
  }
}
.gen__label {
  flex: none;
  width: 72px;
  color: var(--el-text-color-secondary);
}
.gen__value {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--el-text-color-primary);
  word-break: break-all;
  &.is-placeholder {
    font-family: inherit;
    color: var(--el-text-color-placeholder);
  }
}
.gen__copy {
  flex: none;
  min-width: 44px;
  min-height: lp.$component-size-mobile;
}
</style>
