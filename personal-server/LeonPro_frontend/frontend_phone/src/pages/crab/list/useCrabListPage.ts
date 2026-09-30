/** 把 useCrabList 接到路由和接口上（列表页、电脑版详情弹窗页共用） */
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import crabApi from '@/api/crab'
import type { CrabShipment } from '@/api/types'
import { useCrabList } from './useCrabList.js'
import { confirmAction, showToast } from '@/utils/ui'
import { crabTitle, exportShipSheet, shareCrab } from '../crab-actions'

export function useCrabListPage() {
  const route = useRoute()
  const router = useRouter()

  const list = useCrabList({
    api: {
      list: (p) => crabApi.listCrabShipments(p),
      updateStatus: (d) => crabApi.updateCrabStatus(d),
      remove: (ids) => crabApi.deleteCrabShipments(ids),
    },
    getQuery: () => route.query as Record<string, unknown>,
    setQuery: (q) => {
      router.replace({ path: route.path, query: q }).catch(() => {})
    },
  })

  onMounted(() => list.load())
  // 浏览器前进/后退
  watch(
    () => route.query,
    (q) => {
      if (route.name === 'crabList') list.syncFromQuery(q as Record<string, unknown>)
    },
  )

  function goEntry() {
    router.push({ path: '/crab/new', query: { date: list.entryDate() } })
  }
  function openDetail(item: CrabShipment) {
    router.push({ path: `/crab/${encodeURIComponent(item.id)}`, query: list.listQuery() })
  }

  async function remove(item: CrabShipment) {
    const ok = await confirmAction('删除出货单', `确定删除「${crabTitle(item)}」的出货单？删除后不能恢复。`, {
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await list.remove(item)
      showToast('已删除', 'success')
    } catch {
      /* 请求层已提示 */
    }
  }
  async function exportSelected() {
    const done = await exportShipSheet(list.selectedRecords.value, list.label.value)
    if (done) list.toggleSelecting(false)
  }

  return { list, goEntry, openDetail, remove, share: shareCrab, exportSelected }
}
