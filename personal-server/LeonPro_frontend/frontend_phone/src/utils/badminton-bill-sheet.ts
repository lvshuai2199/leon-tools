/** 羽毛球结算图：1080 宽画布，预览层复用发货图 overlay */
import type { BadmintonBallFeeItem, BadmintonBill, BadmintonCourtFeeItem } from '@/api/types'
import {
  ballAmount,
  billTitle,
  bucketPrice,
  courtAmount,
  formatBucket,
  formatMoney,
  intVal,
  money,
  nameHasDate,
  summarize,
} from '@/utils/badminton-bill'
import { canvasToBlob, showSheetPreview } from '@/utils/crab-ship-sheet.js'
import { THEME } from '@/utils/theme-colors.js'
import { showToast } from '@/utils/ui'

const WIDTH = 1080
const PAD = 48
const HEADER_H = 148
const FONT = "'PingFang SC','Microsoft YaHei',sans-serif"
const TEAL = '#0d9488'
const TEAL_DEEP = '#0f766e'
const TEAL_SOFT = '#e7f5f4'
const TEAL_SUB = '#d5f5f2'

type FeeLine = { main: string; note?: string; amount: string }

/**
 * 按宽度折行。数字 / 金额 / 英文单词（如「¥102」「9.50」）作为整体不拆开（美工 10-04：金额不换行）；
 * 只有单个词本身比一行还宽时才逐字拆。
 */
function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const source = String(text || '')
  if (!source) return [] as string[]
  const tokens = source.match(/[A-Za-z0-9¥$.,%:+\-]+|\s+|[^\sA-Za-z0-9¥$.,%:+\-]/gu) || [source]
  const lines: string[] = []
  let line = ''
  const fits = (t: string) => ctx.measureText(t).width <= maxWidth
  for (const tok of tokens) {
    if (fits(line + tok)) {
      line += tok
      continue
    }
    if (line.trim()) lines.push(line.trimEnd())
    line = ''
    const piece = tok.trimStart()
    if (fits(piece)) {
      line = piece
      continue
    }
    for (const ch of piece) {
      if (!fits(line + ch) && line) {
        lines.push(line)
        line = ch
      } else {
        line += ch
      }
    }
  }
  if (line.trim()) lines.push(line.trimEnd())
  return lines
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

function fillRound(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number, fill: string, stroke?: string) {
  roundRect(ctx, x, y, w, h, r)
  ctx.fillStyle = fill
  ctx.fill()
  if (stroke) {
    ctx.strokeStyle = stroke
    ctx.lineWidth = 2
    ctx.stroke()
  }
}

function courtLines(items: BadmintonCourtFeeItem[] | undefined): FeeLine[] {
  return (items || [])
    .filter(
      (item) =>
        intVal(item.courtCount) || money(item.hours) || money(item.unitPrice) || (item.remark || '').trim(),
    )
    .map((item) => ({
      main: `${intVal(item.courtCount)}片 × ${formatMoney(item.hours)}小时 × ${formatMoney(item.unitPrice)}元`,
      note: (item.remark || '').trim() || undefined,
      amount: formatMoney(courtAmount(item)),
    }))
}

function ballLines(items: BadmintonBallFeeItem[] | undefined): FeeLine[] {
  return (items || [])
    .filter((item) => (item.brand || '').trim() || intVal(item.quantity) || money(item.unitPrice) || bucketPrice(item))
    .map((item) => {
      const brand = (item.brand || '').trim() || '未填品牌'
      const bucket = bucketPrice(item)
      if (bucket !== null) {
        // 有整桶价：「亚狮龙7号 3 个（整桶 ¥100 ÷ 12）」 + 「¥25.00」
        return {
          main: `${brand} ${intVal(item.quantity)} 个（整桶 ¥${formatBucket(bucket)} ÷ 12）`,
          amount: `¥${formatMoney(ballAmount(item))}`,
        }
      }
      return {
        main: `${brand} × ${intVal(item.quantity)} × ${formatMoney(item.unitPrice)}元`,
        amount: formatMoney(ballAmount(item)),
      }
    })
}

function measureLines(ctx: CanvasRenderingContext2D, lines: FeeLine[], inner: number) {
  if (!lines.length) return 52
  let h = 0
  lines.forEach((line) => {
    ctx.font = `500 32px ${FONT}`
    const amountW = ctx.measureText(line.amount).width
    const mainLines = wrapText(ctx, line.main, inner - amountW - 28) || [line.main]
    h += Math.max(1, mainLines.length) * 44
    if (line.note) {
      ctx.font = `500 26px ${FONT}`
      h += wrapText(ctx, line.note, inner).length * 34
    }
    h += 10
  })
  return h
}

function measureSection(ctx: CanvasRenderingContext2D, lines: FeeLine[], inner: number) {
  return 36 + 48 + 18 + measureLines(ctx, lines, inner) + 24
}

function drawLines(ctx: CanvasRenderingContext2D, lines: FeeLine[], x: number, y: number, inner: number) {
  if (!lines.length) {
    ctx.fillStyle = THEME.textPlaceholder
    ctx.font = `500 30px ${FONT}`
    ctx.fillText('无', x, y + 36)
    return y + 52
  }
  let cursor = y
  lines.forEach((line) => {
    ctx.font = `600 32px ${FONT}`
    const amountW = ctx.measureText(line.amount).width
    ctx.font = `500 32px ${FONT}`
    const mainLines = wrapText(ctx, line.main, inner - amountW - 28)
    const rows = mainLines.length ? mainLines : [line.main]
    rows.forEach((row, i) => {
      cursor += 44
      ctx.fillStyle = THEME.textPrimary
      ctx.font = `500 32px ${FONT}`
      ctx.fillText(row, x, cursor)
      if (i === 0) {
        ctx.fillStyle = TEAL_DEEP
        ctx.font = `600 32px ${FONT}`
        ctx.fillText(line.amount, x + inner - amountW, cursor)
      }
    })
    if (line.note) {
      ctx.font = `500 26px ${FONT}`
      wrapText(ctx, line.note, inner).forEach((row) => {
        cursor += 34
        ctx.fillStyle = THEME.textSecondary
        ctx.fillText(row, x, cursor)
      })
    }
    cursor += 10
  })
  return cursor
}

function drawSection(
  ctx: CanvasRenderingContext2D,
  title: string,
  total: string,
  lines: FeeLine[],
  y: number,
  inner: number,
) {
  const h = measureSection(ctx, lines, inner)
  fillRound(ctx, PAD, y, WIDTH - PAD * 2, h, 20, THEME.bg, THEME.borderLighter)
  const x = PAD + 28
  ctx.fillStyle = THEME.textPrimary
  ctx.font = `700 34px ${FONT}`
  ctx.fillText(title, x, y + 52)
  ctx.fillStyle = TEAL_DEEP
  ctx.font = `700 34px ${FONT}`
  const tw = ctx.measureText(total).width
  ctx.fillText(total, x + inner - tw, y + 52)
  ctx.strokeStyle = THEME.borderLighter
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(x, y + 72)
  ctx.lineTo(x + inner, y + 72)
  ctx.stroke()
  drawLines(ctx, lines, x, y + 78, inner)
  return y + h + 20
}

export function sheetFilename(bill: Pick<BadmintonBill, 'playDate' | 'title'>) {
  // 新规则的名称已带日期：只用名称（截后 60 字，保留结尾日期）；老记录「日期 标题」，如「2026-10-02 羽林 10.1」
  const title = (bill.title || '').trim()
  const name = title && nameHasDate(title) ? title.slice(-60) : `${bill.playDate || '未填日期'} ${title.slice(0, 40)}`.trim()
  return `羽毛球结算-${name.replace(/[\\/:*?"<>|]/g, '')}.png`
}

export function renderBillSheet(bill: BadmintonBill) {
  const totals = summarize(bill)
  const courts = courtLines(bill.courtItems)
  const balls = ballLines(bill.ballItems)
  const remark = (bill.remark || '').trim()
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('无法绘制结算图')

  const inner = WIDTH - PAD * 2 - 56
  const courtH = measureSection(ctx, courts, inner)
  const ballH = measureSection(ctx, balls, inner)
  const highlightH = 248
  ctx.font = `500 30px ${FONT}`
  const remarkLines = remark ? wrapText(ctx, remark, inner) : []
  const remarkH = remarkLines.length ? 36 + 40 + remarkLines.length * 42 + 28 : 0
  const footerH = 56
  // 抬头副标题「显示名 · 人数」：新规则名称只显示名称，老记录「日期 标题」（规格第 12 条）；完整显示、超宽换行
  const sub = [billTitle(bill), `${totals.people}人`].join('  ·  ')
  ctx.font = `500 28px ${FONT}`
  const subLines = wrapText(ctx, sub, inner - 16)
  const headerH = HEADER_H + Math.max(0, subLines.length - 1) * 38
  const height = PAD + headerH + 20 + courtH + 20 + ballH + 20 + highlightH + (remarkH ? remarkH + 20 : 0) + footerH + PAD
  canvas.width = WIDTH
  canvas.height = Math.max(height, 720)

  ctx.fillStyle = THEME.bgPage
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  fillRound(ctx, PAD, PAD, WIDTH - PAD * 2, headerH, 24, TEAL)
  ctx.fillStyle = THEME.white
  ctx.font = `700 48px ${FONT}`
  ctx.fillText('羽毛球结算', PAD + 36, PAD + 64)
  ctx.font = `500 28px ${FONT}`
  ctx.fillStyle = TEAL_SUB
  subLines.forEach((row, i) => ctx.fillText(row, PAD + 36, PAD + 114 + i * 38))

  let y = PAD + headerH + 20
  y = drawSection(ctx, '场地费', formatMoney(totals.courtTotal), courts, y, inner)
  y = drawSection(ctx, '用球费用', formatMoney(totals.ballTotal), balls, y, inner)

  fillRound(ctx, PAD, y, WIDTH - PAD * 2, highlightH, 20, TEAL_SOFT)
  const x = PAD + 28
  ctx.fillStyle = THEME.textRegular
  ctx.font = `600 30px ${FONT}`
  ctx.fillText('总费用', x, y + 52)
  ctx.fillStyle = TEAL_DEEP
  ctx.font = `700 34px ${FONT}`
  const grand = formatMoney(totals.grandTotal)
  ctx.fillText(grand, x + inner - ctx.measureText(grand).width, y + 52)

  ctx.fillStyle = TEAL
  ctx.font = `600 28px ${FONT}`
  const perLabel = '个人应付'
  const perLabelW = ctx.measureText(perLabel).width
  ctx.fillText(perLabel, PAD + (WIDTH - PAD * 2 - perLabelW) / 2, y + 118)

  ctx.fillStyle = TEAL_DEEP
  ctx.font = `800 64px ${FONT}`
  const perText = `¥ ${formatMoney(totals.perPerson)}`
  const perW = ctx.measureText(perText).width
  ctx.fillText(perText, PAD + (WIDTH - PAD * 2 - perW) / 2, y + 186)

  ctx.fillStyle = THEME.textSecondary
  ctx.font = `500 26px ${FONT}`
  const peopleText = `${totals.people} 人均摊`
  const peopleW = ctx.measureText(peopleText).width
  ctx.fillText(peopleText, PAD + (WIDTH - PAD * 2 - peopleW) / 2, y + 224)
  y += highlightH + 20

  if (remarkLines.length) {
    fillRound(ctx, PAD, y, WIDTH - PAD * 2, remarkH, 20, THEME.bg, THEME.borderLighter)
    ctx.fillStyle = THEME.textSecondary
    ctx.font = `600 28px ${FONT}`
    ctx.fillText('备注', x, y + 48)
    ctx.fillStyle = THEME.textPrimary
    ctx.font = `500 30px ${FONT}`
    let ry = y + 52
    remarkLines.forEach((line) => {
      ry += 42
      ctx.fillText(line, x, ry)
    })
    y += remarkH + 20
  }

  ctx.fillStyle = THEME.textPlaceholder
  ctx.font = `500 24px ${FONT}`
  const foot = bill.operatorName ? `记录人 ${bill.operatorName}  ·  仅作分摊对账` : '仅作分摊对账'
  ctx.fillText(foot, PAD + 8, canvas.height - 28)
  return canvas
}

export async function exportBillSheet(bill: BadmintonBill) {
  try {
    const canvas = renderBillSheet(bill)
    const blob = await canvasToBlob(canvas)
    await showSheetPreview(blob, {
      filename: sheetFilename(bill),
      title: '结算明细预览',
      hint: '可发给球友对账。手机可走系统分享里的「存储图像」，也可长按图片保存。',
      alt: '羽毛球结算明细',
      shareTitle: '羽毛球结算',
      onDownloadFallback: () => showToast('已下载，也可长按预览图存入相册'),
    })
    return true
  } catch (e) {
    console.error(e)
    showToast('生成结算图失败', 'error')
    return false
  }
}
