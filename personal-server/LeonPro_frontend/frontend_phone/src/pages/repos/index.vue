<script setup lang="ts">
/**
 * 仓库：GitHub / Gitee 的概况、提交图和合并。令牌按登录用户存在后端。
 * 列表 /repos，仓库 ?p=&o=&n=，提交再加 &sha=
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Refresh } from '@element-plus/icons-vue'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import StateBlock from '@/components/StateBlock.vue'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import SheetSelect from '@/components/SheetSelect.vue'
import { errorMessage } from '@/api/request'
import {
  clearRepoToken,
  fetchCommit,
  fetchCommits,
  fetchGraph,
  fetchRepoBoard,
  mergeBranches,
  saveRepoToken,
  type CommitBrief,
  type CommitDetail,
  type GraphPage,
  type RepoBoard,
} from '@/api/repos'
import { showToast } from '@/utils/ui'
import CommitGraph from './CommitGraph.vue'
import { canMerge, fileStatusText, formatWhen, headline, mergeText, providerText } from './text.js'

const route = useRoute()
const router = useRouter()

const board = ref<RepoBoard | null>(null)
const boardError = ref('')
const boardLoading = ref(false)
const refreshing = ref(false)
const provider = ref<'all' | 'github' | 'gitee'>('all')
const keyword = ref('')
const tokenOpen = ref(false)
const githubInput = ref('')
const giteeInput = ref('')
const saving = ref('')

const graph = ref<GraphPage | null>(null)
const branchError = ref('')
const branchLoading = ref(false)
const selectedRef = ref('')
const commits = ref<CommitBrief[]>([])
const commitPageNo = ref(1)
const commitDone = ref(false)
const commitLoading = ref(false)
const commitError = ref('')
const merging = ref(false)
const mergeOpen = ref(false)
const mergeHead = ref('')
const mergeBase = ref('')

const detail = ref<CommitDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const openFile = ref('')

let repoSeq = 0
let commitSeq = 0
let detailSeq = 0
let preferAll = false

function queryRepo() {
  const p = String(route.query.p || '')
  const o = String(route.query.o || '')
  const n = String(route.query.n || '')
  if (!p || !o || !n) return null
  return { p, o, n }
}

const repoKey = computed(() => {
  const repo = queryRepo()
  return repo ? `${repo.p}/${repo.o}/${repo.n}` : ''
})
const sha = computed(() => String(route.query.sha || ''))
const view = computed(() => (sha.value ? 'commit' : repoKey.value ? 'repo' : 'list'))
const title = computed(() => {
  if (view.value === 'commit') return detail.value?.shortSha || '提交'
  if (view.value === 'repo') {
    const repo = queryRepo()
    return repo ? `${repo.o}/${repo.n}` : '仓库'
  }
  return '仓库'
})
const backTo = computed(() => {
  if (sha.value) return { path: '/repos', query: queryWithout('sha') }
  if (repoKey.value) return { path: '/repos' }
  return '/'
})

const filtered = computed(() => {
  const list = board.value?.repos || []
  const q = keyword.value.trim().toLowerCase()
  return list.filter((repo) => {
    if (provider.value !== 'all' && repo.provider !== provider.value) return false
    if (!q) return true
    const blob = `${repo.fullName} ${repo.description} ${repo.language} ${repo.lastCommit?.message || ''}`.toLowerCase()
    return blob.includes(q)
  })
})
const githubCount = computed(() => (board.value?.repos || []).filter((r) => r.provider === 'github').length)
const giteeCount = computed(() => (board.value?.repos || []).filter((r) => r.provider === 'gitee').length)
const anyHost = computed(() => !!(board.value && (board.value.github.configured || board.value.gitee.configured)))
const warnings = computed(() => {
  const b = board.value
  if (!b) return []
  return [b.github.warning, b.gitee.warning].filter(Boolean)
})
const logins = computed(() => {
  const b = board.value
  if (!b) return ''
  const parts: string[] = []
  if (b.github.configured && b.github.login) parts.push(`GitHub ${b.github.login}`)
  if (b.gitee.configured && b.gitee.login) parts.push(`Gitee ${b.gitee.login}`)
  return parts.join(' · ')
})
const currentCard = computed(() => {
  const repo = queryRepo()
  if (!repo || !board.value) return null
  return board.value.repos.find((r) => r.provider === repo.p && r.owner === repo.o && r.name === repo.n) || null
})
const branches = computed(() => {
  const page = graph.value
  if (!page) return null
  return { defaultBranch: page.defaultBranch, truncated: page.truncated, branches: page.branches }
})
const currentBranch = computed(() => branches.value?.branches.find((b) => b.name === selectedRef.value) || null)
const mergeIncomplete = computed(() => {
  const page = branches.value
  if (!page) return false
  return page.branches.some((b) => b.name !== page.defaultBranch && !mergeText(b, page.defaultBranch))
})
const showMerge = computed(() => (branches.value?.branches.length || 0) > 1)
const mergeHeadOptions = computed(() =>
  (branches.value?.branches || [])
    .filter((b) => b.name !== mergeBase.value)
    .map((b) => ({ label: b.name, value: b.name, desc: mergeText(b, branches.value?.defaultBranch) })),
)
const mergeBaseOptions = computed(() =>
  (branches.value?.branches || [])
    .filter((b) => b.name !== mergeHead.value)
    .map((b) => ({ label: b.name, value: b.name })),
)
const mergeReady = computed(() => !!mergeHead.value && !!mergeBase.value && mergeHead.value !== mergeBase.value)

function initialRef(data: GraphPage, want: string) {
  const names = data.branches.map((b) => b.name)
  if (want && names.includes(want)) return want
  return ''
}

function wantedRef() {
  if (preferAll) return ''
  return String(route.query.ref || selectedRef.value || '')
}

function githubPlaceholder() {
  return board.value?.github.configured ? `已保存，末四位 ${board.value.github.hint}` : 'GitHub Personal access token'
}
function giteePlaceholder() {
  return board.value?.gitee.configured ? `已保存，末四位 ${board.value.gitee.hint}` : 'Gitee 私人令牌'
}

async function loadBoard(refresh = false) {
  const first = !board.value
  if (first) boardLoading.value = true
  else refreshing.value = true
  boardError.value = ''
  try {
    if (!refresh) {
      const data = await fetchRepoBoard(false)
      board.value = data
      if (!data.stale) return
    }
    refreshing.value = true
    board.value = await fetchRepoBoard(true)
  } catch (e) {
    const msg = errorMessage(e)
    if (first && !board.value) boardError.value = msg
    else showToast(msg, 'error')
  } finally {
    boardLoading.value = false
    refreshing.value = false
  }
}

function applyGraph(data: GraphPage, want: string) {
  graph.value = data
  selectedRef.value = initialRef(data, want)
  if (!selectedRef.value) {
    commits.value = []
    commitError.value = ''
  }
}

async function loadRepo(refresh = false) {
  const repo = queryRepo()
  const id = ++repoSeq
  branchError.value = ''
  if (!repo) {
    graph.value = null
    commits.value = []
    return
  }
  if (!graph.value) branchLoading.value = true
  else if (refresh) refreshing.value = true
  try {
    if (!refresh) {
      const data = await fetchGraph(repo.p, repo.o, repo.n, false)
      if (id !== repoSeq) return
      applyGraph(data, wantedRef())
      if (selectedRef.value) void loadCommits(true)
      if (!data.stale) return
    }
    refreshing.value = true
    const fresh = await fetchGraph(repo.p, repo.o, repo.n, true)
    if (id !== repoSeq) return
    applyGraph(fresh, wantedRef())
    if (selectedRef.value) void loadCommits(true, true)
  } catch (e) {
    if (id !== repoSeq) return
    if (!graph.value) branchError.value = errorMessage(e)
    else showToast(errorMessage(e), 'error')
  } finally {
    if (id === repoSeq) {
      branchLoading.value = false
      refreshing.value = false
    }
  }
}

async function loadCommits(reset: boolean, refresh = false) {
  const repo = queryRepo()
  const refName = selectedRef.value
  if (!repo || !refName) return
  const id = ++commitSeq
  const page = reset ? 1 : commitPageNo.value + 1
  if (reset) {
    commitDone.value = false
    commits.value = []
    commitError.value = ''
  }
  commitLoading.value = true
  try {
    let data = await fetchCommits(repo.p, repo.o, repo.n, refName, page, refresh)
    if (id !== commitSeq) return
    commits.value = reset ? data.commits : commits.value.concat(data.commits)
    commitPageNo.value = page
    commitDone.value = data.commits.length < 30
    if (reset && data.stale && !refresh) {
      const fresh = await fetchCommits(repo.p, repo.o, repo.n, refName, page, true)
      if (id !== commitSeq) return
      commits.value = fresh.commits
      commitDone.value = fresh.commits.length < 30
    }
  } catch (e) {
    if (id !== commitSeq) return
    const msg = errorMessage(e)
    if (reset && !commits.value.length) commitError.value = msg
    else showToast(msg, 'error')
  } finally {
    if (id === commitSeq) commitLoading.value = false
  }
}

async function loadDetail() {
  const repo = queryRepo()
  const current = sha.value
  const id = ++detailSeq
  detail.value = null
  detailError.value = ''
  openFile.value = ''
  if (!repo || !current) return
  detailLoading.value = true
  try {
    const data = await fetchCommit(repo.p, repo.o, repo.n, current)
    if (id !== detailSeq) return
    detail.value = data
  } catch (e) {
    if (id !== detailSeq) return
    detailError.value = errorMessage(e)
  } finally {
    if (id === detailSeq) detailLoading.value = false
  }
}

function queryWithout(drop: string, extra: Record<string, string> = {}) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (key === drop || value == null || value === '') continue
    query[key] = Array.isArray(value) ? String(value[0]) : String(value)
  }
  return { ...query, ...extra }
}

function showAll() {
  if (!selectedRef.value) return
  preferAll = true
  selectedRef.value = ''
  commitSeq += 1
  commits.value = []
  commitError.value = ''
  commitLoading.value = false
  router.replace({ path: '/repos', query: queryWithout('ref') })
}

function onBranch(name: string) {
  if (!name || name === selectedRef.value || !graph.value?.branches.some((b) => b.name === name)) return
  preferAll = false
  selectedRef.value = name
  router.replace({ path: '/repos', query: queryWithout('sha', { ref: name }) })
  loadCommits(true)
}

function onGraphPick(event: Event) {
  const name = (event.target as HTMLSelectElement).value
  if (name) onBranch(name)
  else showAll()
}

function openMerge() {
  const page = graph.value
  if (!page) return
  const preferred = currentBranch.value && currentBranch.value.name !== page.defaultBranch ? currentBranch.value.name : ''
  const fallback = page.branches.find((b) => canMerge(b, page.defaultBranch))?.name || ''
  mergeBase.value = page.defaultBranch
  mergeHead.value = preferred || fallback || page.branches.find((b) => b.name !== page.defaultBranch)?.name || ''
  mergeOpen.value = true
}

async function submitMerge() {
  const repo = queryRepo()
  const head = mergeHead.value
  const base = mergeBase.value
  if (!repo || !head || !base || head === base || merging.value) return
  merging.value = true
  try {
    const result = await mergeBranches(repo.p, repo.o, repo.n, base, head)
    mergeOpen.value = false
    showToast(result?.message || '已合并', 'success')
    await loadRepo()
  } catch {
    // 请求层已经提示失败原因
  } finally {
    merging.value = false
  }
}

function openCommit(id: string) {
  const extra: Record<string, string> = { sha: id }
  if (selectedRef.value) extra.ref = selectedRef.value
  router.push({ path: '/repos', query: queryWithout('sha', extra) })
}

function refresh() {
  if (view.value === 'repo') return loadRepo(true)
  if (view.value === 'commit') return loadDetail()
  return loadBoard(true)
}

async function onSave(which: 'github' | 'gitee') {
  const token = (which === 'github' ? githubInput.value : giteeInput.value).trim()
  if (token.length < 8) {
    showToast('令牌格式不对', 'warning')
    return
  }
  saving.value = which
  try {
    board.value = await saveRepoToken(which, token)
    if (which === 'github') githubInput.value = ''
    else giteeInput.value = ''
    showToast('已保存', 'success')
  } finally {
    saving.value = ''
  }
}

async function onClear(which: 'github' | 'gitee') {
  saving.value = which
  try {
    board.value = await clearRepoToken(which)
    showToast('已清除', 'success')
  } finally {
    saving.value = ''
  }
}

watch(repoKey, () => {
  preferAll = false
  loadRepo()
}, { immediate: true })
watch(sha, () => loadDetail(), { immediate: true })
watch(
  () => String(route.query.ref || ''),
  (ref) => {
    if (!graph.value) return
    if (!ref) {
      if (!selectedRef.value) return
      preferAll = true
      selectedRef.value = ''
      commitSeq += 1
      commits.value = []
      commitError.value = ''
      commitLoading.value = false
      return
    }
    if (ref === selectedRef.value) return
    if (!graph.value.branches.some((b) => b.name === ref)) return
    preferAll = false
    selectedRef.value = ref
    loadCommits(true)
  },
)
loadBoard()
</script>

<template>
  <div class="repos">
    <PageBar :title="title" :back="backTo" :desc="view === 'list' ? '分支合并、最近提交和仓库状态' : ''">
      <template #actions="{ mobile }">
        <IconAction v-if="mobile" :icon="Refresh" label="刷新" :disabled="refreshing || branchLoading || detailLoading || merging" @click="refresh" />
        <el-button v-else :loading="refreshing || branchLoading || detailLoading || merging" @click="refresh">刷新</el-button>
        <template v-if="view === 'list'">
          <IconAction v-if="mobile" :icon="Key" label="令牌" @click="tokenOpen = true" />
          <el-button v-else @click="tokenOpen = true">令牌</el-button>
        </template>
      </template>
    </PageBar>

    <template v-if="view === 'list'">
      <StateBlock v-if="boardLoading" type="loading" title="正在读取仓库" />
      <StateBlock v-else-if="boardError" type="error" :title="boardError" @retry="loadBoard()" />
      <StateBlock v-else-if="board && !anyHost" type="empty" icon="list" title="还没有连接 GitHub 或 Gitee">
        <el-button type="primary" @click="tokenOpen = true">填写令牌</el-button>
      </StateBlock>
      <template v-else-if="board">
        <p v-if="logins" class="logins">{{ logins }}</p>
        <p v-for="w in warnings" :key="w" class="warn">{{ w }}</p>
        <div class="filters">
          <button type="button" :class="{ 'is-on': provider === 'all' }" @click="provider = 'all'">全部 {{ board.repos.length }}</button>
          <button type="button" :class="{ 'is-on': provider === 'github' }" @click="provider = 'github'">GitHub {{ githubCount }}</button>
          <button type="button" :class="{ 'is-on': provider === 'gitee' }" @click="provider = 'gitee'">Gitee {{ giteeCount }}</button>
        </div>
        <el-input v-model="keyword" clearable placeholder="搜索仓库、说明或提交" />
        <StateBlock v-if="!filtered.length" type="empty" icon="list" compact :title="board.repos.length ? '没有匹配的仓库' : '令牌下没有可见仓库'" />
        <div v-else class="cards">
          <router-link
            v-for="repo in filtered"
            :key="`${repo.provider}/${repo.fullName}`"
            class="card"
            :class="{ 'is-archived': repo.archived }"
            :to="{ path: '/repos', query: { p: repo.provider, o: repo.owner, n: repo.name } }"
          >
            <div class="card__top">
              <span class="badge" :class="`badge--${repo.provider}`">{{ providerText(repo.provider) }}</span>
              <span class="tag">{{ repo.privateRepo ? '私有' : '公开' }}</span>
              <span v-if="repo.archived" class="tag">已归档</span>
              <span v-if="repo.language" class="tag">{{ repo.language }}</span>
            </div>
            <h2 class="card__name">{{ repo.fullName }}</h2>
            <p v-if="repo.description" class="card__desc">{{ repo.description }}</p>
            <p class="card__meta">
              <span v-if="repo.defaultBranch">{{ repo.defaultBranch }}</span>
              <span v-if="repo.pushedAt">{{ formatWhen(repo.pushedAt) }}</span>
              <span v-if="repo.stars">★ {{ repo.stars }}</span>
              <span v-if="repo.openIssues">{{ repo.openIssues }} 个 issue</span>
            </p>
            <p v-if="repo.lastCommit" class="card__commit">{{ headline(repo.lastCommit.message) }}</p>
          </router-link>
        </div>
      </template>
    </template>

    <template v-else-if="view === 'repo'">
      <StateBlock v-if="branchLoading && !branches" type="loading" title="正在读取提交图" />
      <StateBlock v-else-if="branchError" type="error" :title="branchError" @retry="loadRepo" />
      <template v-else-if="branches">
        <div v-if="currentCard" class="meta">
          <span class="badge" :class="`badge--${currentCard.provider}`">{{ providerText(currentCard.provider) }}</span>
          <span class="tag">{{ currentCard.privateRepo ? '私有' : '公开' }}</span>
          <span v-if="currentCard.archived" class="tag">已归档</span>
          <span v-if="currentCard.language" class="tag">{{ currentCard.language }}</span>
          <a v-if="currentCard.htmlUrl" class="link" :href="currentCard.htmlUrl" target="_blank" rel="noopener noreferrer">在网页打开</a>
        </div>
        <p v-if="currentCard?.description" class="lead">{{ currentCard.description }}</p>
        <p class="sub">
          {{ branches.defaultBranch ? `默认 ${branches.defaultBranch}` : '没有默认分支' }}
          · {{ branches.branches.length }} 个分支
        </p>
        <div v-if="branches.branches.length" class="graph-head">
          <b class="graph-head__title">分支图</b>
          <span class="graph-head__watch" :title="`正在看：${selectedRef || '全部'}`">正在看：{{ selectedRef || '全部' }}</span>
          <button type="button" class="graph-head__all" :class="{ 'is-on': !selectedRef }" @click="showAll">全部</button>
          <select class="graph-head__select" :value="selectedRef" @change="onGraphPick">
            <option value="">全部</option>
            <option v-for="b in branches.branches" :key="b.name" :value="b.name">{{ b.name }}</option>
          </select>
        </div>
        <template v-if="!selectedRef">
          <p v-if="branches.truncated" class="warn">分支或提交较多，图上只画出最近一段</p>
          <p v-if="mergeIncomplete" class="warn">有的分支暂时没有和默认分支比较的结果</p>
          <StateBlock v-if="!graph || !graph.commits.length" type="empty" icon="list" compact title="这个仓库还没有提交" />
          <CommitGraph v-else :commits="graph.commits" :selected="selectedRef" @open="openCommit" @branch="onBranch" />
        </template>
        <template v-else>
          <StateBlock v-if="commitError" type="error" :title="commitError" @retry="loadCommits(true)" />
          <StateBlock v-else-if="commitLoading && !commits.length" type="loading" title="正在读取提交" />
          <StateBlock v-else-if="!commits.length" type="empty" icon="list" compact title="这个分支还没有提交" />
          <ul v-else class="commits">
            <li v-for="c in commits" :key="c.sha">
              <button type="button" class="commit" :title="headline(c.message)" @click="openCommit(c.sha)">
                <span class="commit__msg">{{ headline(c.message) }}</span>
                <span class="commit__meta">
                  <span>{{ c.shortSha }}</span>
                  <span class="commit__author">{{ c.author || '未知作者' }}</span>
                  <span v-if="formatWhen(c.date)">{{ formatWhen(c.date) }}</span>
                </span>
              </button>
            </li>
          </ul>
          <el-button v-if="commits.length && !commitDone" class="more" :loading="commitLoading" @click="loadCommits(false)">更早的提交</el-button>
        </template>
        <div v-if="showMerge" class="merge-bar">
          <p v-if="currentBranch" class="sub">{{ currentBranch.name }} · {{ mergeText(currentBranch, branches.defaultBranch) }}</p>
          <el-button @click="openMerge">合并</el-button>
        </div>
      </template>
    </template>

    <template v-else>
      <StateBlock v-if="detailLoading && !detail" type="loading" title="正在读取提交" />
      <StateBlock v-else-if="detailError" type="error" :title="detailError" @retry="loadDetail" />
      <article v-else-if="detail" class="detail">
        <h2 class="detail__msg">{{ detail.message || '（没有说明）' }}</h2>
        <p class="sub">{{ detail.author || '未知作者' }} · {{ formatWhen(detail.date) }} · {{ detail.shortSha }}</p>
        <p class="sub">
          <span class="add">+{{ detail.additions }}</span>
          <span class="del">-{{ detail.deletions }}</span>
          <a v-if="detail.htmlUrl" class="link" :href="detail.htmlUrl" target="_blank" rel="noopener noreferrer">在网页打开</a>
        </p>
        <StateBlock v-if="!detail.files.length" type="empty" icon="document" compact title="没有文件差异" />
        <ul v-else class="files">
          <li v-for="f in detail.files" :key="f.filename">
            <button type="button" class="file" @click="openFile = openFile === f.filename ? '' : f.filename">
              <span class="st" :class="`st--${f.status}`">{{ fileStatusText(f.status) }}</span>
              <span class="file__name">{{ f.filename }}</span>
              <span class="add">+{{ f.additions }}</span>
              <span class="del">-{{ f.deletions }}</span>
            </button>
            <pre v-if="openFile === f.filename" class="patch">{{ f.patch || '没有差异文本（可能是二进制文件，或改动太大）' }}</pre>
          </li>
        </ul>
        <p v-if="detail.filesTruncated" class="warn">只显示前 40 个文件</p>
      </article>
    </template>

    <ResponsiveDialog v-model="mergeOpen" title="合并分支" :close-on-click-modal="!merging">
      <p class="hint">先看清从哪条分支合到哪条。点确定后才会合并，取消不会改仓库。有冲突会停下来。</p>
      <div class="merge-field">
        <p class="merge-field__label">把这个分支</p>
        <SheetSelect v-model="mergeHead" title="把这个分支" :options="mergeHeadOptions" />
      </div>
      <div class="merge-field">
        <p class="merge-field__label">合并进</p>
        <SheetSelect v-model="mergeBase" title="合并进" :options="mergeBaseOptions" />
      </div>
      <template #footer>
        <el-button :disabled="merging" @click="mergeOpen = false">取消</el-button>
        <el-button type="primary" :loading="merging" :disabled="!mergeReady" @click="submitMerge">确定</el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog v-model="tokenOpen" title="访问令牌">
      <p class="hint">GitHub 用 Personal access token，看私有仓库和合并都需要 repo 权限。Gitee 用私人令牌，需要 projects 权限。令牌加密存在你的账号下，页面上看不到原文。</p>
      <section class="token">
        <h3>GitHub</h3>
        <el-input v-model="githubInput" type="password" show-password autocomplete="off" :placeholder="githubPlaceholder()" />
        <div class="token__actions">
          <el-button type="primary" :loading="saving === 'github'" :disabled="!githubInput.trim()" @click="onSave('github')">保存</el-button>
          <el-button v-if="board?.github.configured" :loading="saving === 'github'" @click="onClear('github')">清除</el-button>
        </div>
      </section>
      <section class="token">
        <h3>Gitee</h3>
        <el-input v-model="giteeInput" type="password" show-password autocomplete="off" :placeholder="giteePlaceholder()" />
        <div class="token__actions">
          <el-button type="primary" :loading="saving === 'gitee'" :disabled="!giteeInput.trim()" @click="onSave('gitee')">保存</el-button>
          <el-button v-if="board?.gitee.configured" :loading="saving === 'gitee'" @click="onClear('gitee')">清除</el-button>
        </div>
      </section>
    </ResponsiveDialog>
  </div>
</template>

<style scoped lang="scss">
.repos {
  padding-bottom: lp.$space-6;
  @include lp.mobile {
    padding: lp.$page-padding-mobile;
    padding-bottom: lp.$space-6;
  }
}
.logins,
.sub,
.lead,
.hint,
.warn {
  margin: 0 0 lp.$space-3;
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
  line-height: 1.5;
}
.warn {
  color: var(--el-color-warning);
}
.lead,
.detail__msg {
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
.filters {
  display: flex;
  gap: lp.$space-2;
  margin-bottom: lp.$space-3;
  overflow-x: auto;
}
.filters button {
  flex: none;
  min-height: 36px;
  padding: 0 lp.$space-3;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  &.is-on {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}
.graph-head {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  max-width: 960px;
  height: 30px;
  margin-bottom: lp.$space-3;
  overflow: hidden;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.graph-head__title {
  flex: none;
  font-size: lp.$font-size-base;
  font-weight: lp.$font-weight-semibold;
  white-space: nowrap;
}
.graph-head__watch {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 180px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.graph-head__all {
  flex: none;
  height: 24px;
  padding: 0 lp.$space-2;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: lp.$font-size-base;
  white-space: nowrap;
  cursor: pointer;
  &.is-on {
    border-color: #0969da;
    background: #ddf4ff;
    color: #0969da;
  }
}
.graph-head__select {
  flex: none;
  width: 160px;
  max-width: 42%;
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--el-border-color);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-base;
}
.cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: lp.$space-3;
  margin-top: lp.$space-3;
  @include lp.mobile {
    grid-template-columns: 1fr;
  }
}
.card {
  display: flex;
  flex-direction: column;
  gap: lp.$space-1;
  min-height: 76px;
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
  color: inherit;
  text-decoration: none;
  &.is-archived {
    opacity: 0.72;
  }
}
.card__top,
.meta,
.card__meta,
.sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.card__name,
.detail__msg {
  margin: 0;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
}
.card__desc,
.card__commit,
.card__meta {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
}
.card__meta {
  flex-wrap: nowrap;
  overflow: hidden;
  white-space: nowrap;
}
.card__desc,
.card__commit {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__commit {
  color: var(--el-text-color-placeholder);
}
.badge,
.tag,
.st {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 22px;
}
.badge--github {
  background: #24292f;
  color: #fff;
}
.badge--gitee {
  background: #c71d23;
  color: #fff;
}
.tag,
.st {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
.merge-bar {
  display: flex;
  flex-flow: row wrap;
  align-items: center;
  justify-content: flex-end;
  gap: lp.$space-2;
  width: 100%;
  max-width: 960px;
  margin-bottom: lp.$space-4;
}
.merge-bar .sub {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  margin-right: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.merge-field {
  margin-bottom: lp.$space-3;
  :deep(.ss-trigger),
  :deep(.el-select) {
    width: 100%;
  }
}
.merge-field__label {
  margin: 0 0 lp.$space-2;
  color: var(--el-text-color-regular);
  font-size: lp.$font-size-base;
}
.st--added,
.st--add {
  color: var(--el-color-success);
}
.st--removed,
.st--deleted,
.st--delete {
  color: var(--el-color-danger);
}
.commits,
.files {
  margin: 0 0 lp.$space-3;
  padding: 0;
  list-style: none;
}
.commits {
  width: 100%;
  max-width: 960px;
}
.commit {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: lp.$space-2;
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  min-height: 32px;
  max-height: 32px;
  padding: 0;
  overflow: hidden;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.file {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  box-sizing: border-box;
  width: 100%;
  min-height: 36px;
  padding: lp.$space-1 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.commit__msg {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-base;
  font-weight: lp.$font-weight-semibold;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit__meta {
  display: flex;
  flex: none;
  align-items: center;
  gap: lp.$space-2;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
}
.commit__author {
  max-width: 8em;
  overflow: hidden;
  text-overflow: ellipsis;
  @include lp.mobile {
    display: none;
  }
}
.file__name {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  line-height: 1.35;
  word-break: break-word;
}
.more {
  margin-top: lp.$space-3;
}
.add {
  color: var(--el-color-success);
}
.del {
  color: var(--el-color-danger);
}
.link {
  color: var(--el-color-primary);
}
.patch {
  margin: 0 0 lp.$space-2;
  max-height: 280px;
  overflow: auto;
  padding: lp.$space-2;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 12px;
  line-height: 1.45;
  white-space: pre;
}
.token + .token {
  margin-top: lp.$space-4;
}
.token h3 {
  margin: 0 0 lp.$space-2;
  font-size: lp.$font-size-base;
}
.token__actions {
  display: flex;
  gap: lp.$space-2;
  margin-top: lp.$space-2;
}
</style>
