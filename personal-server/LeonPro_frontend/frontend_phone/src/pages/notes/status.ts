export function describe(source: {
  configured?: boolean
  status?: string
  fileCount?: number
  lastCommit?: string | null
  lastSyncTime?: string | null
  lastError?: string | null
} | null) {
  if (!source?.configured) return '还没有配置仓库'
  if (source.status === 'syncing') return '正在同步仓库…'
  if (source.status === 'error') return source.lastError || '同步失败'
  const sha = source.lastCommit ? source.lastCommit.slice(0, 7) : ''
  return `已同步 ${source.fileCount || 0} 篇${sha ? ' · ' + sha : ''}${source.lastSyncTime ? ' · ' + source.lastSyncTime : ''}`
}
