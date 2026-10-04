import { computed, reactive, ref } from 'vue'
import badmintonApi from '@/api/badminton'
import type { BadmintonBill } from '@/api/types'
import { confirmAction, copyText, showToast } from '@/utils/ui'
import { exportBillSheet } from '@/utils/badminton-bill-sheet'
import {
  billTitle,
  bucketPrice,
  bucketPriceInvalid,
  buildSummaryText,
  emptyBall,
  emptyCourt,
  emptyForm,
  syncBucketUnit,
  todayStr,
} from '@/utils/badminton-bill'

export function useBadmintonForm(opts: { onDeleted: () => void }) {
  const form = reactive<BadmintonBill>(emptyForm())
  const snapshot = ref('')
  const loading = ref(false)
  const loadError = ref('')
  const notFound = ref(false)
  const saving = ref(false)

  const dirty = computed(() => snapshot.value !== '' && JSON.stringify(form) !== snapshot.value)

  function remember() {
    snapshot.value = JSON.stringify(form)
  }

  function fill(row: BadmintonBill, playDate?: string) {
    Object.assign(form, emptyForm(playDate || row.playDate))
    form.id = row.id || ''
    form.playDate = row.playDate || todayStr()
    form.title = row.title || ''
    form.participantCount = row.participantCount || 1
    form.remark = row.remark || ''
    form.courtItems = (row.courtItems || []).map((item) => ({
      courtCount: item.courtCount ?? 0,
      hours: Number(item.hours ?? 0),
      unitPrice: Number(item.unitPrice ?? 0),
      remark: item.remark || '',
    }))
    form.ballItems = (row.ballItems || []).map((item) => ({
      brand: item.brand || '',
      quantity: item.quantity ?? 0,
      unitPrice: Number(item.unitPrice ?? 0),
      bucketPrice: item.bucketPrice === null || item.bucketPrice === undefined ? null : Number(item.bucketPrice),
    }))
    if (!form.courtItems?.length) form.courtItems = [emptyCourt()]
    if (!form.ballItems?.length) form.ballItems = [emptyBall()]
    remember()
  }

  function addCourt() {
    form.courtItems = [...(form.courtItems || []), emptyCourt()]
  }
  function removeCourt(index: number) {
    const list = [...(form.courtItems || [])]
    list.splice(index, 1)
    form.courtItems = list.length ? list : [emptyCourt()]
  }
  function addBall() {
    form.ballItems = [...(form.ballItems || []), emptyBall()]
  }
  function removeBall(index: number) {
    const list = [...(form.ballItems || [])]
    list.splice(index, 1)
    form.ballItems = list.length ? list : [emptyBall()]
  }

  async function load(id?: string, playDate?: string) {
    loading.value = true
    loadError.value = ''
    notFound.value = false
    if (!id) {
      fill(emptyForm(playDate), playDate)
      loading.value = false
      return
    }
    try {
      const data = await badmintonApi.get(id)
      fill(data)
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败'
      if (msg.includes('不存在') || msg.includes('无权') || msg.includes('没有权限')) {
        notFound.value = true
      } else {
        loadError.value = msg
      }
    } finally {
      loading.value = false
    }
  }

  async function save() {
    if (!form.playDate) {
      showToast('请选择日期', 'warning')
      return false
    }
    if (!form.participantCount || form.participantCount < 1) {
      showToast('请填写参与人数', 'warning')
      return false
    }
    if ((form.ballItems || []).some((item) => bucketPriceInvalid(item))) {
      showToast('请输入大于 0 的价格', 'warning')
      return false
    }
    // 整桶价 0 / 空 = 没填；有整桶价时单价同步成 round2(整桶/12)（服务端也会重算）
    const ballItems = (form.ballItems || []).map((item) => {
      const row = { ...item, bucketPrice: bucketPrice(item) }
      syncBucketUnit(row)
      return row
    })
    saving.value = true
    try {
      const data = await badmintonApi.save({ ...form, ballItems, id: form.id || undefined })
      fill(data)
      showToast('保存成功', 'success')
      return true
    } catch {
      return false
    } finally {
      saving.value = false
    }
  }

  async function remove() {
    if (!form.id) return false
    const ok = await confirmAction('删除球局', `确定删除「${billTitle(form)}」？删除后不能恢复。`, {
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return false
    try {
      await badmintonApi.remove([form.id])
      showToast('已删除', 'success')
      opts.onDeleted()
      return true
    } catch {
      return false
    }
  }

  function copyBill() {
    return copyText(buildSummaryText(form), '已复制账单')
  }

  function exportSheet() {
    return exportBillSheet(form)
  }

  return {
    form,
    loading,
    loadError,
    notFound,
    saving,
    dirty,
    load,
    save,
    remove,
    copyBill,
    exportSheet,
    addCourt,
    removeCourt,
    addBall,
    removeBall,
  }
}
