/** 注册码页共用数据：可用配置、我的次数、/auth/me 的 regCode（生成页和子用户页都会用、都会刷新） */
import { computed, ref } from 'vue'
import regCodeApi from '@/api/regcode'
import type { RegCodeQuota } from '@/api/types'
import { userStore } from '@/stores/user'

export interface RegCodeConfig {
  id: string
  company?: string
  name?: string
  componentName?: string
}

export function useRegcodeData() {
  const configs = ref<RegCodeConfig[]>([])
  const configsLoading = ref(true)
  const configsError = ref('')
  const quota = ref<RegCodeQuota | null>(null)

  const regCode = computed(() => userStore.regCode.value)

  async function loadConfigs() {
    configsLoading.value = true
    configsError.value = ''
    try {
      const data = await regCodeApi.listRegCodeConfig({ silent: true })
      const list = Array.isArray(data) ? data : Array.isArray(data?.records) ? data.records : []
      configs.value = list.map((c: RegCodeConfig) => ({ ...c, id: String(c.id) }))
    } catch (e) {
      configs.value = []
      configsError.value = (e as Error)?.message || '加载失败'
    } finally {
      configsLoading.value = false
    }
  }

  async function loadQuota() {
    try {
      quota.value = (await regCodeApi.myQuota()) || null
    } catch {
      /* 请求层已提示 */
    }
  }

  /** 子用户变化后刷新：我的次数 + /auth/me（已建数） */
  async function refreshAfterChange() {
    await Promise.all([loadQuota(), userStore.loadMe({ silent: true }).catch(() => false)])
  }

  return { configs, configsLoading, configsError, quota, regCode, loadConfigs, loadQuota, refreshAfterChange }
}

export type RegcodeData = ReturnType<typeof useRegcodeData>
