<script setup lang="ts">
/**
 * 仓库：GitHub / Gitee 的概况、分支合并情况和提交。令牌按登录用户存在后端。
 * 列表 /repos，仓库 ?p=&o=&n=，提交再加 &sha=
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Refresh } from '@element-plus/icons-vue'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import StateBlock from '@/components/StateBlock.vue'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import { errorMessage } from '@/api/request'
import {
  clearRepoToken,
  fetchBranches,
  fetchCommit,
  fetchCommits,
  fetchRepoBoard,
  saveRepoToken,
  type BranchPage,
  type CommitBrief,
  type CommitDetail,
  type RepoBoard,
} from '@/api/repos'
import { showToast } from '@/utils/ui'
import { fileStatusText, formatWhen, headline, mergeText, mergeTone, providerText } from './text.js'

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

const branches = ref<BranchPage | null>(null)
const branchError = ref('')
const branchLoading = ref(false)
const selectedRef = ref('')
const commits = ref<CommitBrief[]>([])
const commitPageNo = ref(1)
const commitDone = ref(false)
const commitLoading = ref(false)
const commitError = ref('')

const detail = ref<CommitDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const openFile = ref('')

let repoSeq = 0
let commitSeq = 0
let detailSeq = 0

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
const currentBranch = computed(() => branches.value?.branches.find((b) => b.name === selectedRef.value) || null)
const mergeIncomplete = computed(() => {
  const page = branches.value
  if (!page) return false
  return page.branches.some((b) => b.name !== page.defaultBranch && !mergeText(b, page.defaultBranch))
})

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
    board.value = await fetchRepoBoard(refresh)
  } catch (e) {
    const msg = errorMessage(e)
    if (first) boardError.value = msg
    else showToast(msg, 'error')
  } finally {
    boardLoading.value = false
    refreshing.value = false
  }
}

async function loadRepo() {
  const repo = queryRepo()
  const id = ++repoSeq
  branches.value = null
  commits.value = []
  branchError.value = ''
  commitError.value = ''
  if (!repo) return
  branchLoading.value = true
  try {
    const data = await fetchBranches(repo.p, repo.o, repo.n)
    if (id !== repoSeq) return
    branches.value = data
    const want = String(route.query.ref || '')
    const names = data.branches.map((b) => b.name)
    selectedRef.value = names.includes(want) ? want : names.includes(data.defaultBranch) ? data.defaultBranch : names[0] || ''
    await loadCommits(true)
  } catch (e) {
    if (id !== repoSeq) return
    branchError.value = errorMessage(e)
  } finally {
    if (id === repoSeq) branchLoading.value = false
  }
}

async function loadCommits(reset: boolean) {
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
    const data = await fetchCommits(repo.p, repo.o, repo.n, refName, page)
    if (id !== commitSeq) return
    commits.value = reset ? data.commits : commits.value.concat(data.commits)
    commitPageNo.value = page
    if (data.commits.length < 30) commitDone.value = true
  } catch (e) {
    if (id !== commitSeq) return
    const msg = errorMessage(e)
    if (reset) commitError.value = msg
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

function onRefChange(name: string) {
  if (!name || name === selectedRef.value) return
  selectedRef.value = name
  router.replace({ path: '/repos', query: queryWithout('sha', { ref: name }) })
  loadCommits(true)
}

function openCommit(id: string) {
  router.push({ path: '/repos', query: { ...route.query, ref: selectedRef.value, sha: id } })
}

function refresh() {
  if (view.value === 'repo') return loadRepo()
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

watch(repoKey, () => loadRepo(), { immediate: true })
watch(sha, () => loadDetail(), { immediate: true })
watch(
  () => String(route.query.ref || ''),
  (ref) => {
    if (!ref || ref === selectedRef.value || !branches.value) return
    if (!branches.value.branches.some((b) => b.name === ref)) return
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
        <IconAction v-if="mobile" :icon="Refresh" label="刷新" :disabled="refreshing || branchLoading || detailLoading" @click="refresh" />
        <el-button v-else :loading="refreshing || branchLoading || detailLoading" @click="refresh">刷新</el-button>
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
      <StateBlock v-if="branchLoading && !branches" type="loading" title="正在读取分支" />
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
        <p v-if="branches.truncated" class="warn">分支较多，只列出前 100 个</p>
        <p v-if="mergeIncomplete" class="warn">有的分支暂时没有和默认分支比较的结果</p>
        <StateBlock v-if="!branches.branches.length" type="empty" icon="list" compact title="这个仓库还没有分支" />
        <template v-else>
          <ul class="branches">
            <li v-for="b in branches.branches" :key="b.name">
              <button
                type="button"
                class="branch"
                :class="{ 'is-on': b.name === selectedRef }"
                @click="onRefChange(b.name)"
              >
                <span class="branch__name">{{ b.name }}</span>
                <span v-if="mergeText(b, branches.defaultBranch)" class="tag" :class="`tag--${mergeTone(b, branches.defaultBranch)}`">
                  {{ mergeText(b, branches.defaultBranch) }}
                </span>
              </button>
            </li>
          </ul>
          <p v-if="currentBranch" class="sub">
            {{ currentBranch.shortSha }}
            <template v-if="currentBranch.author"> · {{ currentBranch.author }}</template>
            <template v-if="currentBranch.date"> · {{ formatWhen(currentBranch.date) }}</template>
          </p>
          <p v-if="currentBranch?.message" class="lead">{{ currentBranch.message }}</p>
          <StateBlock v-if="commitError" type="error" :title="commitError" @retry="loadCommits(true)" />
          <StateBlock v-else-if="commitLoading && !commits.length" type="loading" title="正在读取提交" />
          <StateBlock v-else-if="!commits.length" type="empty" icon="list" compact title="这个分支还没有提交" />
          <ul v-else class="commits">
            <li v-for="c in commits" :key="c.sha">
              <button type="button" class="commit" @click="openCommit(c.sha)">
                <span class="commit__msg">{{ headline(c.message) }}</span>
                <span class="commit__meta">{{ c.shortSha }} · {{ c.author || '未知作者' }} · {{ formatWhen(c.date) }}</span>
              </button>
            </li>
          </ul>
          <el-button v-if="commits.length && !commitDone" class="more" :loading="commitLoading" @click="loadCommits(false)">更早的提交</el-button>
        </template>
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

    <ResponsiveDialog v-model="tokenOpen" title="访问令牌">
      <p class="hint">GitHub 用 Personal access token，私有仓库需要 repo 权限。Gitee 用私人令牌，需要 projects 权限。令牌加密存在你的账号下，页面上看不到原文。</p>
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
.card__desc,
.card__commit {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.tag--base {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.tag--ok {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.tag--open {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
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
.branches {
  margin: 0 0 lp.$space-3;
  padding: 0;
  list-style: none;
}
.branch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: lp.$space-2 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  text-align: left;
  cursor: pointer;
  &.is-on .branch__name {
    color: var(--el-color-primary);
  }
}
.branch__name {
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-semibold;
}
.commits,
.files {
  margin: 0;
  padding: 0;
  list-style: none;
}
.commit,
.file {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  min-height: 64px;
  padding: lp.$space-3 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.file {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  min-height: 44px;
}
.commit__msg,
.file__name {
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  line-height: 1.35;
  word-break: break-word;
}
.commit__meta {
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
}
.file__name {
  flex: 1;
  min-width: 0;
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
  margin: 0 0 lp.$space-3;
  max-height: 280px;
  overflow: auto;
  padding: lp.$space-3;
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
