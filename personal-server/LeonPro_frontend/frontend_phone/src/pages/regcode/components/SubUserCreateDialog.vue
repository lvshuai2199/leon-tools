<script setup lang="ts">
/**
 * 新建子用户：电脑 480 宽弹窗，手机底部抽屉。字段账号、昵称、初始密码（创建人填写），标签在上。
 * 分配次数按配置逐行：左配置名 + 灰字「你还剩 N 次」，右数字输入框（0 ~ 剩余）。
 */
import { computed, reactive, ref } from 'vue'
import regCodeApi from '@/api/regcode'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { showToast } from '@/utils/ui'
import { buildCreateQuotas, createQuotaRows, validateSubUserForm } from '../regcode-quota.js'
import type { RegcodeData } from '../useRegcodeData'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ data: RegcodeData }>()
const emit = defineEmits<{ created: [username: string] }>()
const { isMobile } = useBreakpoint()

const form = reactive({ username: '', nickname: '', password: '' })
const counts = reactive<Record<string, number | undefined>>({})
const saving = ref(false)
const error = ref('')

const rows = computed(() => createQuotaRows(props.data.quota.value, props.data.configs.value))

function onOpen() {
  form.username = ''
  form.nickname = ''
  form.password = ''
  error.value = ''
  for (const k of Object.keys(counts)) delete counts[k]
  for (const r of rows.value) counts[r.configId] = 0
}

async function submit() {
  error.value = validateSubUserForm(form)
  if (error.value) return
  const { quotas, error: qErr } = buildCreateQuotas(rows.value, counts)
  if (qErr) {
    error.value = qErr
    return
  }
  saving.value = true
  try {
    await regCodeApi.createSubUser({
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      password: form.password,
      quotas,
    })
    showToast(`已创建子用户 ${form.username.trim()}`, 'success')
    visible.value = false
    emit('created', form.username.trim())
  } catch {
    /* 用户名已存在、人数已满等业务错误请求层已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ResponsiveDialog v-model="visible" title="新建子用户" :close-on-click-modal="false" @open="onOpen">
    <el-form label-position="top" class="suc" @submit.prevent="submit">
      <el-form-item label="账号" required>
        <el-input v-model="form.username" placeholder="3–20 位字母或数字，登录用" maxlength="20" autocomplete="off" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="form.nickname" placeholder="如 门店一（可不填）" maxlength="20" />
      </el-form-item>
      <el-form-item label="初始密码" required>
        <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位，发给对方登录用" autocomplete="new-password" />
      </el-form-item>

      <div class="suc__section">
        <p class="suc__label">分配次数</p>
        <p class="suc__hint">分给子用户的次数从你的剩余次数里扣除</p>
        <p v-if="!rows.length" class="suc__none">你还没有可分配的配置，可以先建账号，之后再分配</p>
        <div v-for="r in rows" :key="r.configId" class="suc__row">
          <div class="suc__name">
            <span>{{ r.configName }}</span>
            <small>{{ r.remaining == null ? '不限次数' : `你还剩 ${r.remaining} 次` }}</small>
          </div>
          <el-input-number
            v-model="counts[r.configId]"
            :min="0"
            :max="r.remaining == null ? 99999 : r.remaining"
            :disabled="r.remaining === 0"
            :size="isMobile ? 'large' : 'default'"
            :aria-label="`${r.configName} 分配次数`"
            class="suc__num"
          />
        </div>
      </div>
      <p v-if="error" class="suc__error" role="alert">{{ error }}</p>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped lang="scss">
.suc :deep(.el-form-item) {
  margin-bottom: lp.$space-4;
}
.suc__section {
  padding-top: lp.$space-3;
  border-top: 1px solid var(--el-border-color-lighter);
}
.suc__label {
  margin: 0;
  font-weight: lp.$font-weight-medium;
  color: var(--el-text-color-primary);
}
.suc__hint,
.suc__none {
  margin: 2px 0 lp.$space-2;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.suc__row {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  padding: lp.$space-2 0;
}
.suc__name {
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
.suc__num {
  flex: none;
  width: 140px;
}
.suc__error {
  margin: lp.$space-2 0 0;
  color: var(--el-color-danger);
  font-size: lp.$font-size-base;
}
</style>
