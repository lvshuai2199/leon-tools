/**
 * 注册码（/common/...，接口表 #6–#14）。准入由后端 403（注册码访问规则）+ 菜单授权决定，前端不写角色规则。
 * 业务错误（次数不足、用户名已存在、人数已满等）是 HTTP 200 + status 500，请求层直接提示 message。
 */
import { http } from './request'
import type { RegCodeQuota, RegCodeSubUserList, RegCodeSubUserQuota } from './types'

const enc = encodeURIComponent

export const regCodeApi = {
  /** #6 当前用户可用的注册码配置（GET，不带参数，身份只看 token；一个都没有时是空数组） */
  listRegCodeConfig() {
    return http.get<any>('/common/regCodeConfig/list')
  },
  /** #7 当前用户的次数（按配置分别计算，见 RegCodeQuota.items） */
  myQuota() {
    return http.get<RegCodeQuota>('/common/regCodeUser/myQuota')
  },
  /** #8 生成临时注册码（applyId 由后端从 token 取，不再传；扣所选配置 1 次） */
  genTempRegCode(params: { regCode: string; configId: string; company?: string; applyName?: string }) {
    return http.post<Record<string, string>>('/common/regCode/genTempRegCode', params)
  },

  /* ---------- 子用户（#9–#14，界面第二块做） ---------- */

  /** #9 自己建的子用户列表 */
  listSubUsers() {
    return http.get<RegCodeSubUserList>('/common/regCode/subUsers')
  },
  /** #10 新建子用户：密码由创建人填（≥6 位），quotas 从自己的剩余里划拨 */
  createSubUser(data: { username: string; nickname?: string; password: string; quotas: Array<{ configId: string; count: number }> }) {
    return http.post<{ id: string; username: string }>('/common/regCode/subUsers', data)
  },
  /** #11 子用户各配置次数 + 可退回合计 + 自己各配置剩余 */
  getSubUserQuota(id: string) {
    return http.get<RegCodeSubUserQuota>(`/common/regCode/subUsers/${enc(id)}/quota`)
  },
  /** #12 追加（delta>0）或收回未用（delta<0）次数 */
  adjustSubUserQuota(id: string, items: Array<{ configId: string; delta: number }>) {
    return http.post<RegCodeSubUserQuota>(`/common/regCode/subUsers/${enc(id)}/quota`, { items })
  },
  /** #13 停用（0，未用次数退回）/ 启用（1，次数为 0） */
  setSubUserStatus(id: string, status: 0 | 1) {
    return http.post<{ status: number; refunded: Array<{ configId: string; count: number }>; refundedTotal: number }>(
      `/common/regCode/subUsers/${enc(id)}/status`,
      { status },
    )
  },
  /** #14 重置密码：后端生成 10 位新密码，只这一次返回 */
  resetSubUserPassword(id: string) {
    return http.post<{ password: string }>(`/common/regCode/subUsers/${enc(id)}/resetPassword`, {})
  },
}

export default regCodeApi
