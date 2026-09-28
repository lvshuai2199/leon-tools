import type { ComputedRef, Ref } from 'vue'
import type { CrabShipment } from '@/api/types'
import type { Query } from '@/api/request'

export type CrabListMode = 'day' | 'range'
export interface CrabListFilter {
  mode: CrabListMode
  date: string
  start: string
  end: string
  keyword: string
}
export interface CrabListSummary {
  count: number
  unpaid: number
  unshipped: number
  totalQty: number
}
export type CrabStatusField = 'paid' | 'shipped'

export interface CrabListApi {
  list(params: Query): Promise<unknown>
  updateStatus(data: Record<string, unknown>): Promise<unknown>
  remove(ids: string[]): Promise<unknown>
}

export function parseListQuery(query?: Record<string, unknown>, today?: string): CrabListFilter
export function buildListQuery(filter: CrabListFilter): Record<string, string>
export function buildApiParams(filter: CrabListFilter): Query
export function normalizeRecords(data: unknown): CrabShipment[]
export function summarize(records: CrabShipment[]): CrabListSummary
export function dateLabel(filter: CrabListFilter): string
export function entryDate(filter: CrabListFilter): string
export function emptyText(filter: CrabListFilter): string

export interface CrabList {
  filter: CrabListFilter
  records: Ref<CrabShipment[]>
  loading: Ref<boolean>
  loaded: Ref<boolean>
  error: Ref<string>
  summary: ComputedRef<CrabListSummary>
  label: ComputedRef<string>
  empty: ComputedRef<string>
  entryDate(): string
  listQuery(): Record<string, string>
  load(): Promise<void>
  setMode(mode: CrabListMode): Promise<void>
  setDate(date: string): Promise<void>
  shift(delta: number): Promise<void>
  setRange(start: string, end: string): Promise<void>
  search(keyword: string): Promise<void>
  syncFromQuery(query: Record<string, unknown>): Promise<void>
  toggleStatus(item: CrabShipment, field: CrabStatusField): Promise<boolean>
  applyTracking(item: CrabShipment, code: string): Promise<boolean>
  remove(item: CrabShipment): Promise<void>
  selecting: Ref<boolean>
  selectedIds: Ref<string[]>
  selectedRecords: ComputedRef<CrabShipment[]>
  allSelected: ComputedRef<boolean>
  toggleSelecting(on?: boolean): void
  toggleItem(id: string): void
  toggleAll(): void
  setSelected(ids: string[]): void
}

export function useCrabList(deps: {
  api: CrabListApi
  getQuery: () => Record<string, unknown>
  setQuery: (q: Record<string, string>) => void
  today?: string
}): CrabList

export function _clearCrabListCache(): void
