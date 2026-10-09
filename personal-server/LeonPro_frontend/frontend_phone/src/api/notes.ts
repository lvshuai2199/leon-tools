import { API_PREFIX } from '@/config'
import { http } from '@/api/request'
import { userStore } from '@/stores/user'

export interface NoteSource {
  configured: boolean
  repoUrl: string
  branch: string
  tokenSet: boolean
  status: 'idle' | 'syncing' | 'ok' | 'error' | string
  lastCommit: string | null
  lastSyncTime: string | null
  lastCheckTime: string | null
  lastError: string | null
  fileCount: number
}

export interface NoteDoc {
  path: string
  title: string
  size: number
}

export interface NoteContent {
  path: string
  title: string
  content: string
}

export interface NoteDraft {
  id: string
  title: string
  excerpt?: string
  content?: string
  updateTime: string | null
}

const notesApi = {
  source() {
    return http.get<NoteSource>('/app/notes/source')
  },
  docs() {
    return http.get<NoteDoc[]>('/app/notes/docs')
  },
  doc(path: string) {
    return http.get<NoteContent>('/app/notes/doc', { path })
  },
  drafts() {
    return http.get<NoteDraft[]>('/app/notes/drafts')
  },
  draft(id: string) {
    return http.get<NoteDraft>('/app/notes/draft', { id })
  },
  saveDraft(body: { id?: string; title: string; content: string }) {
    return http.post<NoteDraft>('/app/notes/draft', body)
  },
  deleteDraft(id: string) {
    return http.post<void>('/app/notes/draft/delete', { id })
  },
  uploadDraft(id: string) {
    return http.post<{ path: string; commit: string; title: string }>('/app/notes/draft/upload', { id }, { timeout: 120000 })
  },
  async asset(path: string): Promise<Blob> {
    const token = userStore.getToken()
    const resp = await fetch(`${API_PREFIX}/app/notes/asset?path=${encodeURIComponent(path)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!resp.ok) throw new Error('文件加载失败')
    return resp.blob()
  },
}

export default notesApi
