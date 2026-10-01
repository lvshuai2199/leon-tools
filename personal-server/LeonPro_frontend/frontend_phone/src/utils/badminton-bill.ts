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

export function ballAmount(item: BadmintonBallFeeItem): number {
  return money(intVal(item?.quantity) * money(item?.unitPrice))
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
  return { brand: '', quantity: 1, unitPrice: 0 }
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

export function billTitle(item: BadmintonBill) {
  return [item.playDate, item.title].filter(Boolean).join(' ') || '球局'
}

export function buildSummaryText(form: BadmintonBill) {
  const totals = summarize(form)
  const lines: string[] = []
  const head = [form.playDate, form.title].filter(Boolean).join(' ')
  lines.push(head ? `羽毛球计费 ${head}` : '羽毛球计费')
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
      const brand = (item.brand || '').trim() || '未填品牌'
      lines.push(
        `  ${i + 1}. ${brand} × ${intVal(item.quantity)} × ${formatMoney(item.unitPrice)}元 = ${formatMoney(ballAmount(item))}元`,
      )
    })
  }
  lines.push(`人数：${totals.people}人`)
  lines.push(`场地合计：${formatMoney(totals.courtTotal)}元`)
  lines.push(`用球合计：${formatMoney(totals.ballTotal)}元`)
  lines.push(`总计：${formatMoney(totals.grandTotal)}元`)
  lines.push(`个人应付：${formatMoney(totals.perPerson)}元`)
  return lines.join('\n')
}
