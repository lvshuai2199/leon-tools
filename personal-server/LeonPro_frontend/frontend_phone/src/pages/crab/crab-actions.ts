/**
 * 蟹单各页面共用的动作：分享（系统分享 → 复制链接）、扫单号（BarcodeDetector 相机）、图片识别单号、发货清单图。
 */
import type { CrabShipment } from '@/api/types'
import { copyText, showToast } from '@/utils/ui'
import { shareUrl } from '@/utils/crab-parse.js'
import { pickTrackingNoFromImage, scanTrackingNo } from '@/utils/barcode-scan.js'
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

/** 相机扫单号；取消返回空字符串 */
export async function scanTracking(): Promise<string> {
  try {
    return (await scanTrackingNo()) || ''
  } catch (e) {
    if (isAbort(e)) return ''
    console.error(e)
    showToast('扫码失败，可在「更多」里用图片识别', 'error')
    return ''
  }
}

/** 从相册/拍照识别单号；没识别到返回空字符串 */
export async function pickTrackingImage(): Promise<string> {
  try {
    showToast('识别中…')
    const code = (await pickTrackingNoFromImage()) || ''
    if (!code) showToast('没有识别到单号，请换张更清晰的面单照片', 'warning')
    return code
  } catch (e) {
    console.error(e)
    showToast('图片识别失败', 'error')
    return ''
  }
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

/** 删除确认里用的对象名称，如「1. 张三」 */
export function crabTitle(item: Pick<CrabShipment, 'seqNo' | 'customerName'>) {
  const name = item.customerName || '未填姓名'
  return item.seqNo ? `${item.seqNo}. ${name}` : name
}
