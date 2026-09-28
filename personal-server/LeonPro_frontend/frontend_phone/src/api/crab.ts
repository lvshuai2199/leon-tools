/** 螃蟹出货：/app/crabShipment/*，公开分享 /public/crabShipment/{publicId} */
import { http, type Query } from './request'

const BASE = '/app/crabShipment'
const enc = encodeURIComponent

export const crabApi = {
  /** 列表：shipDate 单日，或 shipDateStart/shipDateEnd 区间；customerName / phone 搜索；current/size 分页 */
  listCrabShipments(params: Query) {
    return http.get<any>(`${BASE}/getAll`, params)
  },
  getCrabShipment(id: string) {
    return http.get<any>(`${BASE}/${enc(id)}`)
  },
  saveCrabShipment(data: Record<string, unknown>) {
    return http.post<any>(`${BASE}/save`, data)
  },
  updateCrabStatus(data: Record<string, unknown>) {
    return http.post<any>(`${BASE}/status`, data)
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
  publicCrabShipment(publicId: string) {
    return http.get<any>(`/public/crabShipment/${enc(publicId)}`)
  },
}

export default crabApi
