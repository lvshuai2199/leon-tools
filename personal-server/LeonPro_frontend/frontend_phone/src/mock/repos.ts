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
  branches: { name: string; sha: string; shortSha: string; message: string; author: string; date: string }[]
  commits: Record<string, Commit[]>
  files: Record<string, { filename: string; status: string; additions: number; deletions: number; patch: string }[]>
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
      { name: 'main', sha: 'abc1234def567890', shortSha: 'abc1234', message: '手机端可以看仓库状态', author: 'leon-lv', date: hoursAgo(2) },
      { name: 'dev', sha: 'def5678abc901234', shortSha: 'def5678', message: '调整部署说明', author: 'leon-lv', date: hoursAgo(30) },
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
    },
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
      { name: 'master', sha: 'eee9999fff000011', shortSha: 'eee9999', message: '更新一张配图', author: 'sfnss', date: hoursAgo(20) },
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
  const { branches: _b, commits: _c, files: _f, ...rest } = repo
  return rest
}

function board(tokens: Tokens) {
  return {
    github: host(tokens, 'github'),
    gitee: host(tokens, 'gitee'),
    repos: REPOS.filter((r) => tokens[r.provider as 'github' | 'gitee']).map(card),
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

  const m = /^\/app\/repos\/(github|gitee)\/([^/]+)\/([^/]+)\/(branches|commits)(?:\/([^/]+))?$/.exec(path)
  if (!m || method !== 'GET') return null
  const provider = m[1] as 'github' | 'gitee'
  const owner = decodeURIComponent(m[2])
  const name = decodeURIComponent(m[3])
  const kind = m[4]
  const sha = m[5] ? decodeURIComponent(m[5]) : ''
  if (!tokens[provider]) return fail(`先保存 ${provider === 'github' ? 'GitHub' : 'Gitee'} 令牌`)
  const repo = findRepo(provider, owner, name)
  if (!repo) return fail('不存在，或当前令牌看不到它')
  if (kind === 'branches') {
    return ok({ defaultBranch: repo.defaultBranch, truncated: false, branches: repo.branches })
  }
  if (sha) {
    const commit = Object.values(repo.commits).flat().find((c) => c.sha === sha)
    if (!commit) return fail('提交不存在，或当前令牌看不到它')
    const files = repo.files[sha] || []
    const additions = files.reduce((n, f) => n + f.additions, 0)
    const deletions = files.reduce((n, f) => n + f.deletions, 0)
    return ok({ ...commit, message: commit.message, additions, deletions, filesTruncated: false, files })
  }
  const ref = q.get('ref') || repo.defaultBranch
  const page = Number(q.get('page') || 1)
  const list = repo.commits[ref] || []
  return ok({ ref, page, commits: page > 1 ? [] : list })
}
