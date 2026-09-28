/** 螃蟹出货：/app/crabShipment/*，公开分享 /public/crabShipment/{publicId} */
import { http, type Query, type RequestOptions } from './request'
import type { CrabShipment, CrabShipmentPublic, PageResult } from './types'

const BASE = '/app/crabShipment'
const enc = encodeURIComponent

export const crabApi = {
  /** 列表：shipDate 单日，或 shipDateStart/shipDateEnd 区间；customerName / phone 搜索；current/size 分页 */
  listCrabShipments(params: Query) {
    return http.get<PageResult<CrabShipment> | CrabShipment[]>(`${BASE}/getAll`, params)
  },
  getCrabShipment(id: string, opts: Pick<RequestOptions, 'silent'> = {}) {
    return http.get<CrabShipment>(`${BASE}/${enc(id)}`, undefined, opts)
  },
  saveCrabShipment(data: Record<string, unknown>) {
    return http.post<CrabShipment>(`${BASE}/save`, data)
  },
  updateCrabStatus(data: Record<string, unknown>) {
    return http.post<CrabShipment | null>(`${BASE}/status`, data)
  },
  batchSaveCrabShipments(data: Record<string, unknown>) {
    return http.post<any>(`${BASE}/batchSave`, data)
  },
  parseCrabText(text: string) {
    return http.post<any>(`${BASE}/parse`, { text })
  },
  deleteCrabShipments(ids: string[]) {
    return http.post<any>(`${BASE}/del`, ids)
  },
  /** 公开分享页（免登录） */
  publicCrabShipment(publicId: string, opts: Pick<RequestOptions, 'silent'> = {}) {
    return http.get<CrabShipmentPublic>(`/public/crabShipment/${enc(publicId)}`, undefined, opts)
  },
}

export default crabApi
