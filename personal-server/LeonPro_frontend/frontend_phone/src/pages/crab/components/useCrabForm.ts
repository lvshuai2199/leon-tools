/** 编辑一单（手机详情页、电脑详情弹窗共用） */
import { computed, reactive, ref } from 'vue'
import crabApi from '@/api/crab'
import type { CrabShipment } from '@/api/types'
import { ApiError, isNotFound } from '@/api/request'
import { todayStr } from '@/utils/crab-parse.js'
import { confirmAction, showToast } from '@/utils/ui'
import { copyCrabLink, crabTitle, exportShipSheet, shareCrab } from '../crab-actions'

export interface CrabFormModel {
  id: string
  publicId: string
  shipDate: string
  seqNo: number | null
  customerName: string
  phone: string
  address: string
  spec: string
  quantity: string
  trackingNo: string
  paid: number
  shipped: number
  remark: string
}

function emptyForm(): CrabFormModel {
  return {
    id: '',
    publicId: '',
    shipDate: todayStr(),
    seqNo: null,
    customerName: '',
    phone: '',
    address: '',
    spec: '',
    quantity: '',
    trackingNo: '',
    paid: 0,
    shipped: 0,
    remark: '',
  }
}

function fromRecord(r: CrabShipment): CrabFormModel {
  return {
    ...emptyForm(),
    id: String(r.id ?? ''),
    publicId: String(r.publicId ?? ''),
    shipDate: r.shipDate || todayStr(),
    seqNo: r.seqNo == null ? null : Number(r.seqNo),
    customerName: r.customerName || '',
    phone: r.phone || '',
    address: r.address || '',
    spec: r.spec || '',
    quantity: r.quantity == null ? '' : String(r.quantity),
    trackingNo: r.trackingNo || '',
    paid: r.paid ? 1 : 0,
    shipped: r.shipped ? 1 : 0,
    remark: r.remark || '',
  }
}

export function useCrabForm(opts: { onDeleted?: (form: CrabFormModel) => void; onSaved?: (r: CrabShipment) => void } = {}) {
  const form = reactive<CrabFormModel>(emptyForm())
  const loading = ref(false)
  const loadError = ref('')
  const notFound = ref(false)
  const saving = ref(false)
  /** 最近一次加载/保存后的内容，用来判断有没有没保存的修改 */
  const savedSnap = ref('')
  const snap = () => JSON.stringify(form)
  const dirty = computed(() => !!savedSnap.value && !loading.value && snap() !== savedSnap.value)
  let seq = 0

  async function load(id: string) {
    const my = ++seq
    loading.value = true
    loadError.value = ''
    notFound.value = false
    try {
      const data = await crabApi.getCrabShipment(id, { silent: true })
      if (my !== seq) return
      Object.assign(form, fromRecord(data))
      savedSnap.value = snap()
    } catch (e) {
      if (my !== seq) return
      if (isNotFound(e) || (e instanceof ApiError && e.effectiveStatus === 403)) notFound.value = true
      else loadError.value = (e as Error)?.message || '加载失败'
    } finally {
      if (my === seq) loading.value = false
    }
  }

  async function save() {
    if (!form.customerName.trim()) {
      showToast('请填写姓名', 'warning')
      return false
    }
    const qty = form.quantity.trim()
    if (qty && !/^\d+$/.test(qty)) {
      showToast('数量请填整数', 'warning')
      return false
    }
    saving.value = true
    try {
      const data = await crabApi.saveCrabShipment({ ...form, quantity: qty === '' ? null : Number(qty) })
      if (data) Object.assign(form, fromRecord({ ...form, ...data } as unknown as CrabShipment))
      savedSnap.value = snap()
      showToast('已保存', 'success')
      opts.onSaved?.(data)
      return true
    } catch {
      return false
    } finally {
      saving.value = false
    }
  }

  async function remove() {
    if (!form.id) return false
    const ok = await confirmAction('删除出货单', `确定删除「${crabTitle(form)}」的出货单？删除后不能恢复。`, {
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return false
    try {
      await crabApi.deleteCrabShipments([form.id])
      showToast('已删除', 'success')
      opts.onDeleted?.(form)
      return true
    } catch {
      return false
    }
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
    share: () => shareCrab(form),
    copyLink: () => copyCrabLink(form),
    exportSheet: () =>
      exportShipSheet([{ ...form, quantity: form.quantity === '' ? null : Number(form.quantity) } as unknown as CrabShipment], form.shipDate),
  }
}
