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
  preview(data: Record<string, unknown>) {
    return http.post<BadmintonBill>(`${BASE}/preview`, data)
  },
  remove(ids: string[]) {
    return http.post<boolean>(`${BASE}/del`, ids)
  },
}

export default badmintonApi
