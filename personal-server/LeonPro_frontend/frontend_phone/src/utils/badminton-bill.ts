import type { BadmintonBallFeeItem, BadmintonBill, BadmintonCourtFeeItem } from '@/api/types'

export function money(value: number | string | null | undefined): number {
  const n = Number(value)
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.round(n * 100) / 100
}

export function intVal(value: number | string | null | undefined): number {
  const n = Number(value)
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.floor(n)
}

export function formatMoney(value: number | string | null | undefined): string {
  return money(value).toFixed(2)
}

export function courtAmount(item: BadmintonCourtFeeItem): number {
  return money(intVal(item?.courtCount) * money(item?.hours) * money(item?.unitPrice))
}

/** 一桶 12 个 */
export const BALLS_PER_BUCKET = 12

/** 整桶价：空、0、负数、非数字都当没填，返回 null；否则取到分 */
export function bucketPrice(item: Pick<BadmintonBallFeeItem, 'bucketPrice'> | null | undefined): number | null {
  const n = Number(item?.bucketPrice)
  if (item?.bucketPrice === null || item?.bucketPrice === undefined || !Number.isFinite(n) || n <= 0) return null
  return Math.round(n * 100) / 100
}

/** 整桶价是否非法（负数 / 非数字）：不能保存，提示「请输入大于 0 的价格」 */
export function bucketPriceInvalid(item: Pick<BadmintonBallFeeItem, 'bucketPrice'> | null | undefined): boolean {
  const raw = item?.bucketPrice as unknown
  if (raw === null || raw === undefined || raw === '') return false
  const n = Number(raw)
  return !Number.isFinite(n) || n < 0
}

/** 有整桶价时的单价（只读展示，round2(整桶/12)），与服务端一致 */
export function bucketUnitPrice(bucket: number): number {
  return Math.round(Math.round(bucket * 100) / BALLS_PER_BUCKET) / 100
}

/** 整桶价展示：整数不带小数（¥100），否则两位（¥102.50） */
export function formatBucket(bucket: number): string {
  const cents = Math.round(bucket * 100)
  return cents % 100 === 0 ? String(cents / 100) : (cents / 100).toFixed(2)
}

/**
 * 用球小计，与服务端 BadmintonBilling 一致：
 * 有整桶价 → round2(整桶 / 12 × 数量)（按分整数算，最后才取到分：100 元 × 3 个 = 25.00）；
 * 否则 → round2(数量 × 单价)。
 */
export function ballAmount(item: BadmintonBallFeeItem): number {
  const qty = intVal(item?.quantity)
  const bucket = bucketPrice(item)
  if (bucket !== null) {
    const cents = Math.round(bucket * 100)
    return Math.round((cents * qty) / BALLS_PER_BUCKET) / 100
  }
  return money(qty * money(item?.unitPrice))
}

/** 有整桶价时把单价同步成 round2(整桶/12)（保存前 / 展示用），没有就不动 */
export function syncBucketUnit(item: BadmintonBallFeeItem) {
  const bucket = bucketPrice(item)
  if (bucket !== null) item.unitPrice = bucketUnitPrice(bucket)
}

export function summarize(form: Pick<BadmintonBill, 'participantCount' | 'courtItems' | 'ballItems'>) {
  const courtTotal = money((form?.courtItems || []).reduce((sum, item) => sum + courtAmount(item), 0))
  const ballTotal = money((form?.ballItems || []).reduce((sum, item) => sum + ballAmount(item), 0))
  const grandTotal = money(courtTotal + ballTotal)
  const people = Math.max(1, intVal(form?.participantCount) || 1)
  return { courtTotal, ballTotal, grandTotal, perPerson: money(grandTotal / people), people }
}

export function emptyCourt(): BadmintonCourtFeeItem {
  return { courtCount: 1, hours: 2, unitPrice: 0, remark: '' }
}

export function emptyBall(): BadmintonBallFeeItem {
  return { brand: '', quantity: 1, unitPrice: 0, bucketPrice: null }
}

export function emptyForm(playDate?: string): BadmintonBill {
  return {
    id: '',
    playDate: playDate || todayStr(),
    title: '',
    participantCount: 4,
    remark: '',
    courtItems: [emptyCourt()],
    ballItems: [emptyBall()],
  }
}

export function todayStr() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function shiftDay(dateStr: string, delta: number) {
  const d = new Date(`${dateStr || todayStr()}T00:00:00`)
  d.setDate(d.getDate() + delta)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 名称结尾已是「YYYY-MM-DD」或「YYYY-MM-DD (n)」（新规则保存过的名称） */
const NAME_DATE_TAIL = /\d{4}-\d{2}-\d{2}(?:\s*\(\d{1,4}\))?$/

export function nameHasDate(title?: string | null) {
  return NAME_DATE_TAIL.test((title || '').trim())
}

/**
 * 显示名：名称结尾带日期（新规则）→ 只显示名称；
 * 老记录名称结尾没有日期 → 仍按「日期 + 标题」；都没有 → 「球局」。
 */
export function billTitle(item: Pick<BadmintonBill, 'playDate' | 'title'>) {
  const title = (item.title || '').trim()
  if (title && nameHasDate(title)) return title
  return [item.playDate, title].filter(Boolean).join(' ') || '球局'
}

/** 一条用球的账单文字：有整桶价「3 个（整桶 ¥100 ÷ 12）= ¥25.00」，否则沿用老格式 */
export function ballLineText(item: BadmintonBallFeeItem) {
  const brand = (item.brand || '').trim() || '未填品牌'
  const bucket = bucketPrice(item)
  if (bucket !== null) {
    return `${brand} ${intVal(item.quantity)} 个（整桶 ¥${formatBucket(bucket)} ÷ 12）= ¥${formatMoney(ballAmount(item))}`
  }
  return `${brand} × ${intVal(item.quantity)} × ${formatMoney(item.unitPrice)}元 = ${formatMoney(ballAmount(item))}元`
}

export function buildSummaryText(form: BadmintonBill) {
  const totals = summarize(form)
  const lines: string[] = []
  const head = billTitle(form)
  lines.push(head && head !== '球局' ? `羽毛球计费 ${head}` : '羽毛球计费')
  lines.push('场地费：')
  const courts = form.courtItems || []
  if (!courts.length) {
    lines.push('  无')
  } else {
    courts.forEach((item, i) => {
      const note = item.remark ? `（${item.remark}）` : ''
      lines.push(
        `  ${i + 1}. ${intVal(item.courtCount)}片 × ${formatMoney(item.hours)}小时 × ${formatMoney(item.unitPrice)}元 = ${formatMoney(courtAmount(item))}元${note}`,
      )
    })
  }
  lines.push('用球费用：')
  const balls = form.ballItems || []
  if (!balls.length) {
    lines.push('  无')
  } else {
    balls.forEach((item, i) => {
      lines.push(`  ${i + 1}. ${ballLineText(item)}`)
    })
  }
  lines.push(`人数：${totals.people}人`)
  lines.push(`场地合计：${formatMoney(totals.courtTotal)}元`)
  lines.push(`用球合计：${formatMoney(totals.ballTotal)}元`)
  lines.push(`总计：${formatMoney(totals.grandTotal)}元`)
  lines.push(`个人应付：${formatMoney(totals.perPerson)}元`)
  return lines.join('\n')
}
