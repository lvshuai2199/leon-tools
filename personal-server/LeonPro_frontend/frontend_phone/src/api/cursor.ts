/** 用户端 Cursor 任务。Key 和任务都按当前登录用户走后端，前端不保存 Key。 */
import { http } from './request'

export interface CursorTaskCard {
  id: string
  name: string
  agentStatus: string
  runStatus: string
  repoUrl: string
  updatedAt: string
}

export interface CursorTaskDetail extends CursorTaskCard {
  promptPreview: string
  startingRef: string
  resultText: string
  agentUrl: string
  prUrl: string
  branchName: string
}

export interface CursorBoard {
  configured: boolean
  hint: string
  warning: string
  tasks: CursorTaskCard[]
}

export interface CursorRepo {
  owner: string
  name: string
  url: string
}

export interface CursorRepoList {
  repos: CursorRepo[]
  warning: string
}

export interface CursorQuota {
  available: boolean
  unlimited: boolean
  remainingPercent: number | null
  resetAt: string
  warning: string
}

type Opts = { silent?: boolean }

export function fetchBoard(opts: Opts = {}) {
  return http.get<CursorBoard>('/app/cursor/board', undefined, opts)
}

export function saveCursorKey(apiKey: string) {
  return http.post<CursorBoard>('/app/cursor/key', { apiKey })
}

export function clearCursorKey() {
  return http.post<CursorBoard>('/app/cursor/key/clear', {})
}

export function fetchCursorRepos(opts: Opts = {}) {
  return http.get<CursorRepoList>('/app/cursor/repos', undefined, { timeout: 100_000, ...opts })
}

export function fetchCursorQuota(opts: Opts = {}) {
  return http.get<CursorQuota>('/app/cursor/usage', undefined, { timeout: 30_000, ...opts })
}

export function createCursorTask(data: { prompt: string; repoUrl: string; startingRef?: string; autoCreatePr: boolean }) {
  return http.post<CursorTaskDetail>('/app/cursor/tasks', data)
}

export function fetchCursorTask(id: string, opts: Opts = {}) {
  return http.get<CursorTaskDetail>(`/app/cursor/tasks/${encodeURIComponent(id)}`, undefined, opts)
}

export function followCursorTask(id: string, prompt: string) {
  return http.post<CursorTaskDetail>(`/app/cursor/tasks/${encodeURIComponent(id)}/follow`, { prompt })
}

export function cancelCursorTask(id: string) {
  return http.post<CursorTaskDetail>(`/app/cursor/tasks/${encodeURIComponent(id)}/cancel`, {})
}

export const CURSOR_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '进行中',
  IDLE: '待继续',
  ARCHIVED: '已归档',
  CREATING: '启动中',
  RUNNING: '执行中',
  FINISHED: '已完成',
  ERROR: '失败',
  CANCELLED: '已停止',
  EXPIRED: '已过期',
}

export function cursorStatusText(agentStatus?: string, runStatus?: string) {
  const run = runStatus ? CURSOR_STATUS_LABEL[runStatus] || runStatus : ''
  const agent = agentStatus ? CURSOR_STATUS_LABEL[agentStatus] || agentStatus : ''
  if (run && agent && run !== agent) return `${run}`
  return run || agent || '未知'
}

export function cursorBusy(agentStatus?: string, runStatus?: string) {
  return agentStatus === 'ACTIVE' || runStatus === 'CREATING' || runStatus === 'RUNNING'
}

export function cursorTone(agentStatus?: string, runStatus?: string) {
  const s = runStatus || agentStatus || ''
  if (s === 'ERROR') return 'danger'
  if (s === 'FINISHED' || s === 'IDLE') return 'success'
  if (s === 'CANCELLED' || s === 'ARCHIVED' || s === 'EXPIRED') return 'info'
  return 'primary'
}
