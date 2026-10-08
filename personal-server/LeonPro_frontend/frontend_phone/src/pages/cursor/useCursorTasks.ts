import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  cancelCursorTask,
  clearCursorKey,
  createCursorTask,
  cursorBusy,
  fetchBoard,
  fetchCursorRepos,
  fetchCursorTask,
  followCursorTask,
  saveCursorKey,
  type CursorBoard,
  type CursorRepo,
  type CursorTaskDetail,
} from '@/api/cursor'

export function useCursorTasks() {
  const route = useRoute()
  const router = useRouter()

  const board = ref<CursorBoard>({ configured: false, hint: '', warning: '', tasks: [] })
  const details = ref<Record<string, CursorTaskDetail>>({})
  const selectedId = ref('')
  const loading = ref(true)
  const error = ref('')
  const detailError = ref('')
  const savingKey = ref(false)
  const sending = ref(false)
  const repos = ref<CursorRepo[]>([])
  const reposLoading = ref(false)
  const reposWarning = ref('')
  let reposLoaded = false
  let reposFailedAt = 0
  let timer = 0

  const tasks = computed(() => board.value.tasks)
  const detail = computed(() => (selectedId.value ? details.value[selectedId.value] : undefined))
  const index = computed(() => tasks.value.findIndex((t) => t.id === selectedId.value))
  const busy = computed(() => cursorBusy(detail.value?.agentStatus, detail.value?.runStatus))

  function applyBoard(next: CursorBoard) {
    board.value = { ...next, warning: next.warning || '', tasks: next.tasks || [] }
    if (!board.value.tasks.some((t) => t.id === selectedId.value)) {
      const fromQuery = typeof route.query.task === 'string' ? route.query.task : ''
      const keep = board.value.tasks.find((t) => t.id === fromQuery)?.id || board.value.tasks[0]?.id || ''
      selectedId.value = keep
    }
  }

  async function loadDetail(id: string, silent: boolean) {
    if (!id) return
    const data = await fetchCursorTask(id, { silent })
    details.value = { ...details.value, [id]: data }
    const i = board.value.tasks.findIndex((t) => t.id === id)
    if (i >= 0) {
      const tasksNext = board.value.tasks.slice()
      tasksNext[i] = { ...tasksNext[i], name: data.name, agentStatus: data.agentStatus, runStatus: data.runStatus, updatedAt: data.updatedAt }
      board.value = { ...board.value, tasks: tasksNext }
    }
  }

  function select(id: string) {
    selectedId.value = id
    detailError.value = ''
    router.replace({ query: { ...route.query, task: id } })
    const cached = details.value[id]
    return loadDetail(id, !!cached).catch((e: Error) => {
      if (!details.value[id]) detailError.value = e.message || '加载失败'
    })
  }

  function step(delta: number) {
    const n = tasks.value.length
    if (n < 2) return
    const i = index.value < 0 ? 0 : index.value
    const next = tasks.value[(i + delta + n) % n]
    select(next.id)
  }

  async function reload(silent = false) {
    if (!silent) {
      loading.value = true
      error.value = ''
    }
    try {
      applyBoard(await fetchBoard({ silent }))
      if (selectedId.value) await loadDetail(selectedId.value, silent || !!details.value[selectedId.value])
    } catch (e) {
      if (!silent) error.value = (e as Error).message || '加载失败'
    } finally {
      loading.value = false
      arm()
    }
  }

  function arm() {
    window.clearTimeout(timer)
    if (document.visibilityState === 'hidden') return
    const hot = board.value.tasks.some((t) => cursorBusy(t.agentStatus, t.runStatus)) || busy.value
    if (!hot) return
    timer = window.setTimeout(() => reload(true), 4000)
  }

  function onVisible() {
    if (document.visibilityState === 'visible') reload(true)
  }

  function resetRepos() {
    reposLoaded = false
    reposFailedAt = 0
    repos.value = []
    reposWarning.value = ''
  }

  async function loadRepos(force = false) {
    if (!board.value.configured) return
    if (!force && reposLoaded) return
    if (!force && reposFailedAt && Date.now() - reposFailedAt < 60_000) return
    reposLoading.value = true
    reposWarning.value = ''
    try {
      const data = await fetchCursorRepos({ silent: true })
      repos.value = data.repos || []
      reposWarning.value = data.warning || ''
      reposLoaded = true
      reposFailedAt = 0
    } catch (e) {
      reposFailedAt = Date.now()
      reposWarning.value = (e as Error).message || '仓库列表加载失败'
    } finally {
      reposLoading.value = false
    }
  }

  async function saveKey(apiKey: string) {
    savingKey.value = true
    try {
      applyBoard(await saveCursorKey(apiKey))
      resetRepos()
      if (selectedId.value) await loadDetail(selectedId.value, false)
    } finally {
      savingKey.value = false
      arm()
    }
  }

  async function clearKey() {
    applyBoard(await clearCursorKey())
    details.value = {}
    selectedId.value = ''
    resetRepos()
    router.replace({ query: { ...route.query, task: undefined } })
  }

  async function createTask(payload: { prompt: string; repoUrl: string; startingRef: string; autoCreatePr: boolean }) {
    sending.value = true
    try {
      const created = await createCursorTask(payload)
      details.value = { ...details.value, [created.id]: created }
      await reload(true)
      select(created.id)
    } finally {
      sending.value = false
    }
  }

  async function follow(prompt: string) {
    if (!selectedId.value) return
    sending.value = true
    try {
      const next = await followCursorTask(selectedId.value, prompt)
      details.value = { ...details.value, [next.id]: next }
      await reload(true)
    } finally {
      sending.value = false
    }
  }

  async function cancel() {
    if (!selectedId.value) return
    sending.value = true
    try {
      const next = await cancelCursorTask(selectedId.value)
      details.value = { ...details.value, [next.id]: next }
      await reload(true)
    } finally {
      sending.value = false
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisible)
    reload(false)
  })
  onBeforeUnmount(() => {
    window.clearTimeout(timer)
    document.removeEventListener('visibilitychange', onVisible)
  })

  return {
    board,
    tasks,
    detail,
    selectedId,
    index,
    busy,
    loading,
    error,
    detailError,
    savingKey,
    sending,
    repos,
    reposLoading,
    reposWarning,
    reload,
    loadRepos,
    select,
    step,
    saveKey,
    clearKey,
    createTask,
    follow,
    cancel,
  }
}
