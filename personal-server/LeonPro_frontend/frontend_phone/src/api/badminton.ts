/** 羽毛球计费：/app/badmintonBill/* */
import { http, type Query } from './request'
import type { BadmintonBill, PageResult } from './types'

const BASE = '/app/badmintonBill'
const enc = encodeURIComponent

export const badmintonApi = {
  list(params: Query) {
    return http.get<PageResult<BadmintonBill> | BadmintonBill[]>(`${BASE}/getAll`, params)
  },
  get(id: string) {
    return http.get<BadmintonBill>(`${BASE}/${enc(id)}`)
  },
  save(data: Record<string, unknown>) {
    return http.post<BadmintonBill>(`${BASE}/save`, data)
  },
  /** 名称预览：与保存时的名称规则一致；silent，失败由调用方显示兜底文案 */
  namePreview(data: { id?: string; title?: string; playDate?: string }, signal?: AbortSignal) {
    return http.post<{ name: string; playDate: string }>(`${BASE}/namePreview`, data, { silent: true, signal })
  },
  preview(data: Record<string, unknown>) {
    return http.post<BadmintonBill>(`${BASE}/preview`, data)
  },
  remove(ids: string[]) {
    return http.post<boolean>(`${BASE}/del`, ids)
  },
}

export default badmintonApi
