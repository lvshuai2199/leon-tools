import { computed, inject, onMounted, onUnmounted, provide, ref, watch, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import notesApi, { type NoteContent, type NoteDoc, type NoteSource } from '@/api/notes'
import { errorMessage } from '@/api/request'

export interface NotesState {
  source: Ref<NoteSource | null>
  docs: Ref<NoteDoc[]>
  keyword: Ref<string>
  loading: Ref<boolean>
  error: Ref<string>
  doc: Ref<NoteContent | null>
  docLoading: Ref<boolean>
  docError: Ref<string>
  tab: ComputedRef<'repo' | 'draft'>
  activePath: ComputedRef<string>
  loadList: (silent?: boolean) => Promise<void>
  setTab: (tab: 'repo' | 'draft') => void
  openDoc: (path: string) => void
  closeDoc: () => void
}

const KEY: InjectionKey<NotesState> = Symbol('notes')

export function provideNotes() {
  const route = useRoute()
  const router = useRouter()
  const source = ref<NoteSource | null>(null)
  const docs = ref<NoteDoc[]>([])
  const keyword = ref('')
  const loading = ref(false)
  const error = ref('')
  const doc = ref<NoteContent | null>(null)
  const docLoading = ref(false)
  const docError = ref('')
  const tab = computed<'repo' | 'draft'>(() => (route.query.tab === 'draft' ? 'draft' : 'repo'))
  const activePath = computed(() => (typeof route.query.doc === 'string' ? route.query.doc : ''))

  let timer: ReturnType<typeof setTimeout> | undefined
  let alive = true
  let docSeq = 0

  async function loadList(silent = false) {
    if (!silent) loading.value = true
    error.value = ''
    try {
      const [src, list] = await Promise.all([notesApi.source(), notesApi.docs()])
      source.value = src
      docs.value = list || []
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
      schedule()
    }
  }

  function schedule() {
    if (timer) clearTimeout(timer)
    if (!alive) return
    const wait = source.value?.status === 'syncing' ? 2000 : 30000
    timer = setTimeout(() => loadList(true), wait)
  }

  function setTab(next: 'repo' | 'draft') {
    const query: Record<string, string> = {}
    if (next === 'draft') query.tab = 'draft'
    if (next === 'repo' && activePath.value) query.doc = activePath.value
    if (next === 'draft' && typeof route.query.draft === 'string') query.draft = route.query.draft
    if (next === 'draft' && route.query.fresh === '1') query.fresh = '1'
    router.replace({ query })
  }

  function openDoc(path: string) {
    router.push({ query: { doc: path } })
  }

  function closeDoc() {
    router.push({ query: {} })
  }

  async function loadDoc(path: string) {
    const seq = ++docSeq
    docLoading.value = true
    docError.value = ''
    try {
      const data = await notesApi.doc(path)
      if (seq !== docSeq) return
      doc.value = data
    } catch (e) {
      if (seq !== docSeq) return
      doc.value = null
      docError.value = errorMessage(e)
    } finally {
      if (seq === docSeq) docLoading.value = false
    }
  }

  watch(activePath, (path) => {
    if (path) loadDoc(path)
    else {
      docSeq++
      doc.value = null
      docError.value = ''
    }
  })

  onMounted(() => {
    loadList()
    if (activePath.value) loadDoc(activePath.value)
  })
  onUnmounted(() => {
    alive = false
    if (timer) clearTimeout(timer)
  })

  const api: NotesState = {
    source,
    docs,
    keyword,
    loading,
    error,
    doc,
    docLoading,
    docError,
    tab,
    activePath,
    loadList,
    setTab,
    openDoc,
    closeDoc,
  }
  provide(KEY, api)
  return api
}

export function useNotes() {
  const api = inject(KEY)
  if (!api) throw new Error('笔记状态还没有准备好')
  return api
}
