/** 快速录入：粘贴识别 / 手动 → 预览可改 → 批量入库 */
import { computed, nextTick, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import crabApi from '@/api/crab'
import { parseCrabOrders, todayStr } from '@/utils/crab-parse.js'
import { confirmAction, showToast } from '@/utils/ui'
import { useLeaveGuard } from '@/composables/useLeaveGuard'

export interface EntryRow {
  key: number
  seqNo?: number | null
  customerName: string
  phone: string
  address: string
  spec: string
  quantity: string
}
export type EntryTab = 'paste' | 'manual'

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/
let keySeq = 0

function emptyManual() {
  return { customerName: '', phone: '', address: '', spec: '', quantity: '' }
}

export function useCrabEntry() {
  const route = useRoute()

  const q = typeof route.query.date === 'string' ? route.query.date : ''
  const shipDate = ref(DATE_RE.test(q) ? q : todayStr())
  const tab = ref<EntryTab>('paste')
  const rawText = ref('')
  const parsing = ref(false)
  const saving = ref(false)
  const rows = ref<EntryRow[]>([])
  const manual = reactive(emptyManual())

  function toRow(item: Record<string, unknown>): EntryRow {
    return {
      key: ++keySeq,
      seqNo: (item.seqNo as number) ?? null,
      customerName: String(item.customerName ?? ''),
      phone: String(item.phone ?? ''),
      address: String(item.address ?? ''),
      spec: String(item.spec ?? ''),
      quantity: item.quantity == null ? '' : String(item.quantity),
    }
  }

  function applyRows(list: unknown) {
    const items = Array.isArray(list) ? list.filter((r) => r && (r.customerName || r.phone)) : []
    if (!items.length) {
      showToast('没有识别到出货行，请检查格式或改用手动', 'warning')
      return
    }
    rows.value = items.map(toRow)
    showToast(`识别到 ${items.length} 条`, 'success')
  }

  async function parseText() {
    const text = rawText.value.trim()
    if (!text) {
      showToast('请先粘贴文本', 'warning')
      return
    }
    parsing.value = true
    try {
      const local = parseCrabOrders(text)
      if (local.length) {
        applyRows(local)
        return
      }
      const data = await crabApi.parseCrabText(text)
      applyRows(data?.records || [])
    } catch {
      /* 请求层已提示 */
    } finally {
      parsing.value = false
    }
  }

  function onPaste() {
    nextTick(() => {
      if (rawText.value.trim()) parseText()
    })
  }

  function pushManual() {
    if (!manual.customerName.trim()) {
      showToast('请填写姓名', 'warning')
      return
    }
    rows.value.push(toRow({ ...manual }))
    Object.assign(manual, emptyManual())
    showToast('已加入识别结果', 'success')
  }

  function rowTitle(row: EntryRow, index: number) {
    return `第 ${index + 1} 条${row.customerName ? `（${row.customerName}）` : ''}`
  }

  async function removeRow(index: number) {
    const row = rows.value[index]
    if (!row) return
    const name = row.customerName.trim()
    const ok = await confirmAction('删除这一条', name ? `确定删除「${name}」这一条？` : '确定删除这一条？', { confirmText: '删除', danger: true })
    if (ok) rows.value.splice(index, 1)
  }

  async function clearAll() {
    if (!rows.value.length) return
    const ok = await confirmAction('清空识别结果', `将清空 ${rows.value.length} 条识别结果，未入库的内容会丢失。`, {
      confirmText: '清空',
      danger: true,
    })
    if (ok) rows.value = []
  }

  function validate() {
    const bad = rows.value.findIndex((r) => !r.customerName.trim())
    if (bad >= 0) {
      showToast(`第 ${bad + 1} 条没有填姓名`, 'warning')
      return false
    }
    const badQty = rows.value.findIndex((r) => r.quantity.trim() && !/^\d+$/.test(r.quantity.trim()))
    if (badQty >= 0) {
      showToast(`第 ${badQty + 1} 条数量请填整数`, 'warning')
      return false
    }
    return true
  }

  /** 有没入库的内容：识别结果、粘贴框、手动表单里填了东西 */
  const dirty = computed(
    () => rows.value.length > 0 || !!rawText.value.trim() || Object.values(manual).some((v) => String(v).trim() !== ''),
  )

  // 有没入库的内容时，返回 / 后退先确认「有未保存的内容，确定离开？」
  const { leave } = useLeaveGuard(() => dirty.value)

  /** 入库成功后回列表（不再确认） */
  function goList() {
    return leave({ path: '/crab', query: { date: shipDate.value } })
  }

  async function saveAll() {
    if (!rows.value.length || saving.value || !validate()) return
    saving.value = true
    try {
      await crabApi.batchSaveCrabShipments({
        shipDate: shipDate.value,
        records: rows.value.map(({ key: _k, ...r }) => ({ ...r, quantity: r.quantity.trim() === '' ? null : Number(r.quantity) })),
      })
      showToast(`已入库 ${rows.value.length} 条`, 'success')
      rows.value = []
      rawText.value = ''
      goList()
    } catch {
      /* 请求层已提示 */
    } finally {
      saving.value = false
    }
  }

  return {
    shipDate,
    tab,
    rawText,
    parsing,
    saving,
    rows,
    manual,
    dirty,
    parseText,
    onPaste,
    pushManual,
    removeRow,
    clearAll,
    saveAll,
    goList,
    rowTitle,
  }
}
