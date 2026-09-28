import type { RegCodeMeInfo, RegCodeQuota, RegCodeQuotaItem, RegCodeSubUserQuota } from '@/api/types'

export interface LimitState {
  text: string
  over: boolean
  full: boolean
  canCreate: boolean
}
export interface CreateQuotaRow {
  configId: string
  configName: string
  /** 不限次数为 null */
  remaining: number | null
}
export interface AdjustRow {
  configId: string
  configName: string
  allocated: number
  used: number
  /** 创建人该配置剩余；不限次数为 null */
  creatorRemaining: number | null
}
export type AdjustMode = 'add' | 'revoke'

export function needsSubUserListCheck(regCode: RegCodeMeInfo | null | undefined): boolean
export function showSubUserTab(regCode: RegCodeMeInfo | null | undefined, listCount?: number | null): boolean
export function limitState(createdCount: number, maxSubUsers: number): LimitState
export function sumQuota(items: Array<Pick<RegCodeQuotaItem, 'used' | 'allocated'>>): { used: number; allocated: number; remaining: number }
export function isExhausted(usedTotal: number, allocatedTotal: number): boolean
export function usageText(usedTotal: number, allocatedTotal: number): string
export function sortSubUsers<T extends { status: number }>(items: T[]): T[]
export function remainingForConfig(quota: RegCodeQuota | null | undefined, configId: string | number | undefined): number | null
export function createQuotaRows(
  myQuota: RegCodeQuota | null | undefined,
  configs?: Array<{ id: string | number; name?: string; componentName?: string }>,
): CreateQuotaRow[]
export function buildCreateQuotas(rows: CreateQuotaRow[], values: Record<string, number | undefined>): { quotas: Array<{ configId: string; count: number }>; error: string }
export function validateSubUserForm(form: { username: string; password: string }): string
export function adjustRows(quota: RegCodeSubUserQuota | null | undefined, creatorUnlimited?: boolean): AdjustRow[]
export function maxAdd(row: AdjustRow): number | null
export function maxRevoke(row: AdjustRow): number
export function buildDeltas(rows: AdjustRow[], mode: AdjustMode, values: Record<string, number | undefined>): { items: Array<{ configId: string; delta: number }>; error: string }
export function refundableTotal(quota: Partial<RegCodeSubUserQuota> | null | undefined): number
