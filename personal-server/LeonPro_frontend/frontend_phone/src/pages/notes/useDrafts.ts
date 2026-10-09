import { computed, inject, nextTick, onMounted, provide, ref, watch, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import notesApi, { type NoteDraft } from '@/api/notes'
import { errorMessage } from '@/api/request'
import { confirmAction, showToast } from '@/utils/ui'
import { useNotes, type NotesState } from './useNotes'

const LOCAL_KEY = 'leonpro-note-draft-cache'

interface LocalCache {
  id: string
  title: string
  content: string
  at: number
}

export interface DraftsState {
  list: Ref<NoteDraft[]>
  loading: Ref<boolean>
  error: Ref<string>
  title: Ref<string>
  content: Ref<string>
  currentId: Ref<string>
  editing: ComputedRef<boolean>
  saveState: Ref<string>
  uploading: Ref<boolean>
  openDraft: (id: string) => void
  createNew: () => void
  closeEditor: () => void
  remove: () => Promise<void>
  upload: () => Promise<void>
  reload: () => Promise<void>
}

const KEY: InjectionKey<DraftsState> = Symbol('drafts')

export function provideDrafts(notes: NotesState = useNotes()) {
  const route = useRoute()
  const router = useRouter()
  const list = ref<NoteDraft[]>([])
  const loading = ref(false)
  const error = ref('')
  const title = ref('')
  const content = ref('')
  const currentId = ref('')
  const saveState = ref('')
  const uploading = ref(false)
  const hydrating = ref(false)
  const editing = computed(() => route.query.tab === 'draft' && (route.query.fresh === '1' || typeof route.query.draft === 'string'))

  let timer: ReturnType<typeof setTimeout> | undefined
  let seq = 0

  function readLocal(): LocalCache | null {
    try {
      const raw = localStorage.getItem(LOCAL_KEY)
      return raw ? (JSON.parse(raw) as LocalCache) : null
    } catch {
      return null
    }
  }

  function writeLocal() {
    const cache: LocalCache = { id: currentId.value, title: title.value, content: content.value, at: Date.now() }
    localStorage.setItem(LOCAL_KEY, JSON.stringify(cache))
  }

  async function reload() {
    loading.value = true
    error.value = ''
    try {
      list.value = (await notesApi.drafts()) || []
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  function openDraft(id: string) {
    if (timer) clearTimeout(timer)
    router.push({ query: { tab: 'draft', draft: id } })
  }

  function createNew() {
    if (timer) clearTimeout(timer)
    hydrating.value = true
    currentId.value = ''
    title.value = ''
    content.value = ''
    saveState.value = ''
    router.push({ query: { tab: 'draft', fresh: '1' } })
    nextTick(() => {
      hydrating.value = false
    })
  }

  function closeEditor() {
    if (timer) clearTimeout(timer)
    router.push({ query: { tab: 'draft' } })
  }

  async function loadOne(id: string) {
    if (timer) clearTimeout(timer)
    hydrating.value = true
    saveState.value = ''
    try {
      const data = await notesApi.draft(id)
      currentId.value = data.id
      title.value = data.title || ''
      content.value = data.content || ''
      const local = readLocal()
      const serverAt = data.updateTime ? Date.parse(data.updateTime.replace(' ', 'T')) : 0
      if (local && local.id === data.id && local.at > serverAt && (local.title !== title.value || local.content !== content.value)) {
        title.value = local.title
        content.value = local.content
        saveState.value = '有未上传的本地缓存'
        scheduleSave()
      } else {
        saveState.value = data.updateTime ? `已缓存 ${data.updateTime}` : '已缓存'
      }
    } catch (e) {
      showToast(errorMessage(e), 'error')
      closeEditor()
    } finally {
      nextTick(() => {
        hydrating.value = false
      })
    }
  }

  function scheduleSave() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(save, 700)
  }

  async function save() {
    if (!title.value.trim() && !content.value && !currentId.value) return
    const my = ++seq
    const snapshot = { id: currentId.value, title: title.value, content: content.value }
    saveState.value = '缓存中'
    try {
      const saved = await notesApi.saveDraft({
        id: snapshot.id || undefined,
        title: snapshot.title,
        content: snapshot.content,
      })
      if (my !== seq) return
      if (!currentId.value) {
        currentId.value = saved.id
        router.replace({ query: { tab: 'draft', draft: saved.id } })
      }
      saveState.value = saved.updateTime ? `已缓存 ${saved.updateTime}` : '已缓存'
      const idx = list.value.findIndex((d) => d.id === saved.id)
      const row: NoteDraft = { id: saved.id, title: saved.title || '未命名', excerpt: firstLine(saved.content), updateTime: saved.updateTime }
      if (idx >= 0) list.value.splice(idx, 1, row)
      else list.value.unshift(row)
    } catch (e) {
      if (my !== seq) return
      saveState.value = `缓存失败：${errorMessage(e)}`
    }
  }

  async function remove() {
    if (!currentId.value) {
      closeEditor()
      return
    }
    const ok = await confirmAction('删除这篇缓存？', '还没上传的内容会丢掉。', { danger: true, confirmText: '删除' })
    if (!ok) return
    try {
      await notesApi.deleteDraft(currentId.value)
      list.value = list.value.filter((d) => d.id !== currentId.value)
      localStorage.removeItem(LOCAL_KEY)
      showToast('已删除', 'success')
      closeEditor()
    } catch (e) {
      showToast(errorMessage(e), 'error')
    }
  }

  async function upload() {
    if (!content.value.trim()) {
      showToast('内容是空的，还不能上传', 'warning')
      return
    }
    if (timer) clearTimeout(timer)
    await save()
    if (!currentId.value) {
      showToast('还没缓存成功', 'warning')
      return
    }
    const ok = await confirmAction('上传到仓库？', '会提交到当前分支的「随手记」目录，这篇缓存随后删除。', { confirmText: '上传' })
    if (!ok) return
    uploading.value = true
    try {
      const result = await notesApi.uploadDraft(currentId.value)
      list.value = list.value.filter((d) => d.id !== currentId.value)
      localStorage.removeItem(LOCAL_KEY)
      currentId.value = ''
      title.value = ''
      content.value = ''
      showToast('已上传', 'success')
      await notes.loadList(true)
      notes.openDoc(result.path)
    } catch (e) {
      showToast(errorMessage(e), 'error')
    } finally {
      uploading.value = false
    }
  }

  watch([title, content], () => {
    if (hydrating.value) return
    writeLocal()
    scheduleSave()
  })

  watch(
    () => [route.query.tab, route.query.draft, route.query.fresh] as const,
    ([tab, draft, fresh]) => {
      if (tab !== 'draft') return
      if (typeof draft === 'string' && draft && draft !== currentId.value) loadOne(draft)
      else if (fresh === '1' && !draft) {
        hydrating.value = true
        currentId.value = ''
        title.value = ''
        content.value = ''
        saveState.value = ''
        nextTick(() => {
          hydrating.value = false
        })
      }
    },
  )

  onMounted(() => {
    reload()
    if (route.query.tab === 'draft' && typeof route.query.draft === 'string') loadOne(route.query.draft)
  })

  const api: DraftsState = {
    list,
    loading,
    error,
    title,
    content,
    currentId,
    editing,
    saveState,
    uploading,
    openDraft,
    createNew,
    closeEditor,
    remove,
    upload,
    reload,
  }
  provide(KEY, api)
  return api
}

export function useDrafts() {
  const api = inject(KEY)
  if (!api) throw new Error('随手记状态还没有准备好')
  return api
}

function firstLine(content?: string) {
  return String(content || '').replace(/\s+/g, ' ').trim().slice(0, 80)
}
