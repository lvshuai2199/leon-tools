/**
 * 蟹单各页面共用的动作：分享（系统分享 → 复制链接）、发货清单图。
 */
import type { CrabShipment } from '@/api/types'
import { copyText, showToast } from '@/utils/ui'
import { shareUrl } from '@/utils/crab-parse.js'
import { canvasToBlob, renderShipSheet, showSheetPreview } from '@/utils/crab-ship-sheet.js'

const isAbort = (e: unknown) => !!e && typeof e === 'object' && (e as { name?: string }).name === 'AbortError'

/** 分享一单：有 navigator.share 用系统分享，否则复制链接 */
export async function shareCrab(item: Pick<CrabShipment, 'publicId' | 'customerName'>) {
  const url = shareUrl(item.publicId)
  if (!url) {
    showToast('这一单还没有分享链接', 'warning')
    return
  }
  const title = `${item.customerName || '出货单'}的螃蟹出货状态`
  if (navigator.share) {
    try {
      await navigator.share({ title, url, text: title })
      return
    } catch (e) {
      if (isAbort(e)) return
    }
  }
  await copyText(url, '分享链接已复制')
}

/** 复制分享链接 */
export async function copyCrabLink(item: Pick<CrabShipment, 'publicId'>) {
  const url = shareUrl(item.publicId)
  if (!url) {
    showToast('这一单还没有分享链接', 'warning')
    return
  }
  await copyText(url, '分享链接已复制')
}

/** 生成发货清单图并预览（存相册 / 下载） */
export async function exportShipSheet(rows: CrabShipment[], label: string) {
  if (!rows.length) {
    showToast('请先选择出货单', 'warning')
    return false
  }
  try {
    const canvas = renderShipSheet(rows, { date: label })
    const blob = await canvasToBlob(canvas)
    await showSheetPreview(blob, {
      filename: `螃蟹发货清单-${label}.png`,
      onDownloadFallback: () => showToast('已下载，也可长按预览图存入相册'),
    })
    return true
  } catch (e) {
    console.error(e)
    showToast('生成发货图失败', 'error')
    return false
  }
}

/** 删除确认里用的对象名称：只写姓名，不带列表序号 */
export function crabTitle(item: Pick<CrabShipment, 'customerName'>) {
  return item.customerName || '未填姓名'
}
