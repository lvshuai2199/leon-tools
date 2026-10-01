/** 羽毛球计费列表：筛日期、搜标题、删记录 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Query } from '@/api/request'
import badmintonApi from '@/api/badminton'
import type { BadmintonBill } from '@/api/types'
import { confirmAction, showToast } from '@/utils/ui'
import { exportBillSheet } from '@/utils/badminton-bill-sheet'
import { billTitle, formatMoney, money, shiftDay, todayStr } from '@/utils/badminton-bill'

export type DateMode = 'day' | 'range' | 'all'

export function useBadmintonListPage() {
  const route = useRoute()
  const router = useRouter()

  const filter = reactive({
    mode: 'all' as DateMode,
    date: todayStr(),
    start: todayStr(),
    end: todayStr(),
    keyword: '',
  })
  const records = ref<BadmintonBill[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  const summary = computed(() => {
    const count = records.value.length
    const total = records.value.reduce((sum, item) => sum + money(item.grandTotal), 0)
    return { count, total: formatMoney(total) }
  })
  const empty = computed(() => {
    if (filter.mode === 'day') return `${filter.date} 还没有球局`
    if (filter.mode === 'range') return '这段时间还没有球局'
    return '还没有球局'
  })

  function queryParams(): Query {
    const params: Query = { current: 1, size: 200, title: filter.keyword.trim() || undefined }
    if (filter.mode === 'range') {
      params.playDateStart = filter.start
      params.playDateEnd = filter.end
    } else if (filter.mode === 'day') {
      params.playDate = filter.date
    }
    return params
  }

  function listQuery() {
    const q: Record<string, string> = {}
    if (filter.mode === 'day') q.date = filter.date
    if (filter.mode === 'range') {
      q.start = filter.start
      q.end = filter.end
    }
    if (filter.keyword.trim()) q.q = filter.keyword.trim()
    return q
  }

  function syncFromQuery(q: Record<string, unknown>) {
    const date = typeof q.date === 'string' ? q.date : ''
    const start = typeof q.start === 'string' ? q.start : ''
    const end = typeof q.end === 'string' ? q.end : ''
    const keyword = typeof q.q === 'string' ? q.q : ''
    if (start && end) {
      filter.mode = 'range'
      filter.start = start
      filter.end = end
    } else if (date) {
      filter.mode = 'day'
      filter.date = date
    } else {
      filter.mode = 'all'
    }
    filter.keyword = keyword
  }

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await badmintonApi.list(queryParams())
      records.value = Array.isArray(data) ? data : data?.records || []
      loaded.value = true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载失败'
      records.value = []
    } finally {
      loading.value = false
    }
  }

  function setMode(mode: DateMode) {
    filter.mode = mode
    if (mode === 'day') filter.date = filter.date || todayStr()
    if (mode === 'range') {
      filter.start = filter.start || todayStr()
      filter.end = filter.end || todayStr()
    }
    router.replace({ path: '/badminton', query: listQuery() }).catch(() => {})
    load()
  }
  function setDate(date: string) {
    filter.date = date
    router.replace({ path: '/badminton', query: listQuery() }).catch(() => {})
    load()
  }
  function setRange(start: string, end: string) {
    filter.start = start
    filter.end = end
    router.replace({ path: '/badminton', query: listQuery() }).catch(() => {})
    load()
  }
  function shift(delta: number) {
    setDate(shiftDay(filter.date, delta))
  }
  function search(keyword: string) {
    filter.keyword = keyword
    router.replace({ path: '/badminton', query: listQuery() }).catch(() => {})
    load()
  }

  function goNew() {
    const q = filter.mode === 'day' ? { date: filter.date } : listQuery()
    router.push({ path: '/badminton/new', query: q })
  }
  function openDetail(item: BadmintonBill) {
    if (!item.id) return
    router.push({ path: `/badminton/${encodeURIComponent(item.id)}`, query: listQuery() })
  }

  async function exportSheet(item: BadmintonBill) {
    if (!item.id) return
    try {
      const full = await badmintonApi.get(item.id)
      await exportBillSheet(full)
    } catch {
      /* 请求层已提示 */
    }
  }

  async function remove(item: BadmintonBill) {
    if (!item.id) return
    const ok = await confirmAction('删除球局', `确定删除「${billTitle(item)}」？删除后不能恢复。`, {
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await badmintonApi.remove([item.id])
      showToast('已删除', 'success')
      await load()
    } catch {
      /* 请求层已提示 */
    }
  }

  onMounted(() => {
    syncFromQuery(route.query as Record<string, unknown>)
    load()
  })
  watch(
    () => route.query,
    (q) => {
      if (route.name === 'badmintonList') {
        syncFromQuery(q as Record<string, unknown>)
        load()
      }
    },
  )

  return {
    filter,
    records,
    loading,
    loaded,
    error,
    summary,
    empty,
    setMode,
    setDate,
    setRange,
    shift,
    search,
    load,
    goNew,
    openDetail,
    exportSheet,
    remove,
    listQuery,
  }
}
