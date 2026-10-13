/** 用户端 GitHub / Gitee 仓库。令牌只提交给后端，页面不留原文。 */
import { http } from './request'

export interface RepoHost {
  configured: boolean
  hint: string
  login: string
  warning: string
}

export interface CommitBrief {
  sha: string
  shortSha: string
  message: string
  author: string
  date: string
  htmlUrl: string
}

export interface RepoCard {
  provider: string
  owner: string
  name: string
  fullName: string
  privateRepo: boolean
  archived: boolean
  description: string
  defaultBranch: string
  language: string
  htmlUrl: string
  pushedAt: string
  stars: number
  openIssues: number
  lastCommit: CommitBrief | null
}

export interface RepoBoard {
  github: RepoHost
  gitee: RepoHost
  repos: RepoCard[]
  /** 超过半天的缓存。页面先显示，再拉新的换上。 */
  stale?: boolean
}

export interface RepoBranch {
  name: string
  sha: string
  shortSha: string
  message: string
  author: string
  date: string
  /** default / identical / merged / ahead / diverged / unmerged，空字符串表示没比出来 */
  merge: string
  ahead: number
  behind: number
}

export interface BranchPage {
  defaultBranch: string
  truncated: boolean
  branches: RepoBranch[]
}

export interface CommitPage {
  ref: string
  page: number
  commits: CommitBrief[]
  stale?: boolean
}

export interface FileChange {
  filename: string
  status: string
  additions: number
  deletions: number
  patch: string
}

export interface CommitDetail {
  sha: string
  shortSha: string
  message: string
  author: string
  date: string
  htmlUrl: string
  additions: number
  deletions: number
  filesTruncated: boolean
  files: FileChange[]
}

type Opts = { silent?: boolean; timeout?: number }

const slow: Opts = { silent: true, timeout: 40_000 }

function seg(value: string) {
  return encodeURIComponent(value)
}

function repoPath(provider: string, owner: string, name: string) {
  return `/app/repos/${seg(provider)}/${seg(owner)}/${seg(name)}`
}

export function fetchRepoBoard(refresh = false) {
  return http.get<RepoBoard>('/app/repos/board', refresh ? { refresh: true } : undefined, slow)
}

export function saveRepoToken(provider: string, token: string) {
  return http.post<RepoBoard>('/app/repos/token', { provider, token }, { timeout: 30_000 })
}

export function clearRepoToken(provider: string) {
  return http.post<RepoBoard>('/app/repos/token/clear', { provider }, { timeout: 30_000 })
}

export function fetchBranches(provider: string, owner: string, name: string) {
  return http.get<BranchPage>(`${repoPath(provider, owner, name)}/branches`, undefined, slow)
}

export function fetchCommits(provider: string, owner: string, name: string, ref: string, page: number, refresh = false) {
  return http.get<CommitPage>(`${repoPath(provider, owner, name)}/commits`, { ref, page, refresh: refresh || undefined }, slow)
}

export function fetchCommit(provider: string, owner: string, name: string, sha: string) {
  return http.get<CommitDetail>(`${repoPath(provider, owner, name)}/commits/${seg(sha)}`, undefined, slow)
}

export interface GraphCommit {
  sha: string
  shortSha: string
  message: string
  author: string
  date: string
  parents: string[]
  branches: string[]
}

export interface GraphPage {
  defaultBranch: string
  truncated: boolean
  branches: RepoBranch[]
  commits: GraphCommit[]
  stale?: boolean
}

export interface MergeResult {
  outcome: string
  sha: string
  message: string
}

export function fetchGraph(provider: string, owner: string, name: string, refresh = false) {
  return http.get<GraphPage>(`${repoPath(provider, owner, name)}/graph`, refresh ? { refresh: true } : undefined, slow)
}

export function mergeBranches(provider: string, owner: string, name: string, base: string, head: string) {
  return http.post<MergeResult>(`${repoPath(provider, owner, name)}/merge`, { base, head }, { timeout: 40_000 })
}
