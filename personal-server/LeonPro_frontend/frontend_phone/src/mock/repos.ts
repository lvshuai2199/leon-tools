/** 示例数据里的 GitHub / Gitee 仓库。令牌原文不回给页面。 */
type Commit = {
  sha: string
  shortSha: string
  message: string
  author: string
  date: string
  htmlUrl: string
}

type Repo = {
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
  lastCommit: Commit | null
  branches: {
    name: string
    sha: string
    shortSha: string
    message: string
    author: string
    date: string
    merge: string
    ahead: number
    behind: number
  }[]
  commits: Record<string, Commit[]>
  files: Record<string, { filename: string; status: string; additions: number; deletions: number; patch: string }[]>
  history: {
    sha: string
    shortSha: string
    message: string
    author: string
    date: string
    parents: string[]
    branches: string[]
  }[]
}

const hoursAgo = (h: number) => new Date(Date.now() - h * 3600_000).toISOString()

const REPOS: Repo[] = [
  {
    provider: 'github',
    owner: 'leon-lv',
    name: 'leon-tools',
    fullName: 'leon-lv/leon-tools',
    privateRepo: true,
    archived: false,
    description: '个人工具和用户端',
    defaultBranch: 'main',
    language: 'Java',
    htmlUrl: 'https://github.com/leon-lv/leon-tools',
    pushedAt: hoursAgo(2),
    stars: 1,
    openIssues: 0,
    lastCommit: {
      sha: 'abc1234def567890',
      shortSha: 'abc1234',
      message: '手机端可以看仓库状态',
      author: 'leon-lv',
      date: hoursAgo(2),
      htmlUrl: 'https://github.com/leon-lv/leon-tools/commit/abc1234def567890',
    },
    branches: [
      { name: 'main', sha: 'abc1234def567890', shortSha: 'abc1234', message: '手机端可以看仓库状态', author: 'leon-lv', date: hoursAgo(2), merge: 'default', ahead: 0, behind: 0 },
      { name: 'dev', sha: 'def5678abc901234', shortSha: 'def5678', message: '调整部署说明', author: 'leon-lv', date: hoursAgo(30), merge: 'diverged', ahead: 2, behind: 1 },
      { name: 'release', sha: 'bbb2222ccc333444', shortSha: 'bbb2222', message: '补笔记同步', author: 'leon-lv', date: hoursAgo(26), merge: 'merged', ahead: 0, behind: 4 },
    ],
    commits: {
      main: [
        {
          sha: 'abc1234def567890',
          shortSha: 'abc1234',
          message: '手机端可以看仓库状态',
          author: 'leon-lv',
          date: hoursAgo(2),
          htmlUrl: 'https://github.com/leon-lv/leon-tools/commit/abc1234def567890',
        },
        {
          sha: 'bbb2222ccc333444',
          shortSha: 'bbb2222',
          message: '补笔记同步',
          author: 'leon-lv',
          date: hoursAgo(26),
          htmlUrl: 'https://github.com/leon-lv/leon-tools/commit/bbb2222ccc333444',
        },
      ],
      dev: [
        {
          sha: 'def5678abc901234',
          shortSha: 'def5678',
          message: '调整部署说明',
          author: 'leon-lv',
          date: hoursAgo(30),
          htmlUrl: 'https://github.com/leon-lv/leon-tools/commit/def5678abc901234',
        },
      ],
      release: [
        {
          sha: 'ccc3333ddd444555',
          shortSha: 'ccc3333',
          message: '已经合进 main',
          author: 'leon-lv',
          date: hoursAgo(80),
          htmlUrl: 'https://github.com/leon-lv/leon-tools/commit/ccc3333ddd444555',
        },
      ],
    },
    history: [
      { sha: 'def5678abc901234', shortSha: 'def5678', message: '调整部署说明', author: 'leon-lv', date: hoursAgo(1), parents: ['ddd4444eee555666'], branches: ['dev'] },
      { sha: 'abc1234def567890', shortSha: 'abc1234', message: '手机端可以看仓库状态', author: 'leon-lv', date: hoursAgo(2), parents: ['bbb2222ccc333444'], branches: ['main'] },
      { sha: 'ddd4444eee555666', shortSha: 'ddd4444', message: '分支上的修改', author: 'leon-lv', date: hoursAgo(10), parents: ['bbb2222ccc333444'], branches: [] },
      { sha: 'bbb2222ccc333444', shortSha: 'bbb2222', message: '补笔记同步', author: 'leon-lv', date: hoursAgo(26), parents: ['aaa0000bbb111222'], branches: ['release'] },
      { sha: 'aaa0000bbb111222', shortSha: 'aaa0000', message: '初始提交', author: 'leon-lv', date: hoursAgo(80), parents: [], branches: [] },
    ],
    files: {
      abc1234def567890: [
        {
          filename: 'personal-server/LeonPro_frontend/frontend_phone/src/pages/repos/index.vue',
          status: 'added',
          additions: 20,
          deletions: 0,
          patch: '@@ -0,0 +1,3 @@\n+仓库页\n+分支\n+提交\n',
        },
      ],
    },
  },
  {
    provider: 'gitee',
    owner: 'sfnss',
    name: 'typora-pic',
    fullName: 'sfnss/typora-pic',
    privateRepo: false,
    archived: false,
    description: '图床',
    defaultBranch: 'master',
    language: '',
    htmlUrl: 'https://gitee.com/sfnss/typora-pic',
    pushedAt: hoursAgo(20),
    stars: 0,
    openIssues: 1,
    lastCommit: {
      sha: 'eee9999fff000011',
      shortSha: 'eee9999',
      message: '更新一张配图',
      author: 'sfnss',
      date: hoursAgo(20),
      htmlUrl: 'https://gitee.com/sfnss/typora-pic/commit/eee9999fff000011',
    },
    branches: [
      { name: 'master', sha: 'eee9999fff000011', shortSha: 'eee9999', message: '更新一张配图', author: 'sfnss', date: hoursAgo(20), merge: 'default', ahead: 0, behind: 0 },
    ],
    commits: {
      master: [
        {
          sha: 'eee9999fff000011',
          shortSha: 'eee9999',
          message: '更新一张配图',
          author: 'sfnss',
          date: hoursAgo(20),
          htmlUrl: 'https://gitee.com/sfnss/typora-pic/commit/eee9999fff000011',
        },
      ],
    },
    history: [
      { sha: 'eee9999fff000011', shortSha: 'eee9999', message: '更新一张配图', author: 'sfnss', date: hoursAgo(20), parents: [], branches: ['master'] },
    ],
    files: {},
  },
]

type Tokens = { github: string; gitee: string }
const byUser = new Map<string, Tokens>()

function tokensOf(username: string) {
  let t = byUser.get(username)
  if (!t) {
    t = { github: 'ub01', gitee: 'ee02' }
    byUser.set(username, t)
  }
  return t
}

function host(tokens: Tokens, provider: 'github' | 'gitee') {
  const hint = tokens[provider]
  const login = provider === 'github' ? 'leon-lv' : 'sfnss'
  return { configured: !!hint, hint, login: hint ? login : '', warning: '' }
}

function card(repo: Repo) {
  const { branches: _b, commits: _c, files: _f, history: _h, ...rest } = repo
  return rest
}

function flatCommits(repo: Repo) {
  const list = Object.values(repo.commits).flat()
  for (const item of repo.history || []) {
    if (!list.some((commit) => commit.sha === item.sha)) {
      list.push({
        sha: item.sha,
        shortSha: item.shortSha,
        message: item.message,
        author: item.author,
        date: item.date,
        htmlUrl: '',
      })
    }
  }
  return list
}

function board(tokens: Tokens) {
  return {
    github: host(tokens, 'github'),
    gitee: host(tokens, 'gitee'),
    repos: REPOS.filter((r) => tokens[r.provider as 'github' | 'gitee']).map(card),
    stale: false,
  }
}

function findRepo(provider: string, owner: string, name: string) {
  return REPOS.find((r) => r.provider === provider && r.owner === owner && r.name === name)
}

export function handleRepos(username: string, path: string, method: string, q: URLSearchParams, body: Record<string, any>) {
  const tokens = tokensOf(username)
  const fail = (message: string) => ({ status: 200, body: { status: 500, message, data: null } })
  const ok = (data: unknown) => ({ status: 200, body: { status: 200, message: 'success', data } })

  if (path === '/app/repos/board' && method === 'GET') return ok(board(tokens))
  if (path === '/app/repos/token' && method === 'POST') {
    const provider = String(body.provider || '')
    const token = String(body.token || '').trim()
    if (provider !== 'github' && provider !== 'gitee') return fail('只支持 GitHub 和 Gitee')
    if (token.length < 8) return fail('令牌格式不对')
    tokens[provider] = token.slice(-4)
    return ok(board(tokens))
  }
  if (path === '/app/repos/token/clear' && method === 'POST') {
    const provider = String(body.provider || '')
    if (provider !== 'github' && provider !== 'gitee') return fail('只支持 GitHub 和 Gitee')
    tokens[provider] = ''
    return ok(board(tokens))
  }

  const m = /^\/app\/repos\/(github|gitee)\/([^/]+)\/([^/]+)\/(branches|commits|graph|merge)(?:\/([^/]+))?$/.exec(path)
  if (!m) return null
  const provider = m[1] as 'github' | 'gitee'
  const owner = decodeURIComponent(m[2])
  const name = decodeURIComponent(m[3])
  const kind = m[4]
  const sha = m[5] ? decodeURIComponent(m[5]) : ''
  if (kind === 'merge' ? method !== 'POST' : method !== 'GET') return null
  if (!tokens[provider]) return fail(`先保存 ${provider === 'github' ? 'GitHub' : 'Gitee'} 令牌`)
  const repo = findRepo(provider, owner, name)
  if (!repo) return fail('不存在，或当前令牌看不到它')
  if (kind === 'branches') {
    return ok({ defaultBranch: repo.defaultBranch, truncated: false, branches: repo.branches })
  }
  if (kind === 'graph') {
    return ok({ defaultBranch: repo.defaultBranch, truncated: false, branches: repo.branches, commits: repo.history, stale: false })
  }
  if (kind === 'merge') {
    const baseName = String(body.base || '')
    const headName = String(body.head || '')
    const base = repo.branches.find((branch) => branch.name === baseName)
    const head = repo.branches.find((branch) => branch.name === headName)
    if (!base || !head) return fail('分支不存在，或当前令牌看不到它')
    if (baseName === headName) return fail('不能把分支合并到自己')
    if (head.merge === 'merged' || head.merge === 'identical' || head.sha === base.sha) {
      return ok({ outcome: 'already', sha: '', message: `已经在 ${baseName} 里` })
    }
    const mergedSha = 'fff0000aaa111222'
    for (const item of repo.history) item.branches = item.branches.filter((branch) => branch !== baseName)
    repo.history.unshift({
      sha: mergedSha,
      shortSha: 'fff0000',
      message: `Merge ${headName} into ${baseName}`,
      author: 'leon-lv',
      date: new Date().toISOString(),
      parents: [base.sha, head.sha],
      branches: [baseName],
    })
    const merged: Commit = {
      sha: mergedSha,
      shortSha: 'fff0000',
      message: `Merge ${headName} into ${baseName}`,
      author: 'leon-lv',
      date: new Date().toISOString(),
      htmlUrl: `${repo.htmlUrl}/commit/${mergedSha}`,
    }
    repo.commits[baseName] = [merged, ...(repo.commits[baseName] || [])]
    repo.lastCommit = merged
    base.sha = mergedSha
    base.shortSha = 'fff0000'
    base.message = merged.message
    base.date = merged.date
    head.merge = 'merged'
    head.ahead = 0
    return ok({ outcome: 'merged', sha: mergedSha, message: `已合并进 ${baseName}` })
  }
  if (sha) {
    const commit = flatCommits(repo).find((c) => c.sha === sha)
    if (!commit) return fail('提交不存在，或当前令牌看不到它')
    const files = repo.files[sha] || []
    const additions = files.reduce((n, f) => n + f.additions, 0)
    const deletions = files.reduce((n, f) => n + f.deletions, 0)
    return ok({ ...commit, message: commit.message, additions, deletions, filesTruncated: false, files })
  }
  const ref = q.get('ref') || repo.defaultBranch
  const page = Number(q.get('page') || 1)
  const list = repo.commits[ref] || []
  return ok({ ref, page, commits: page > 1 ? [] : list, stale: false })
}
