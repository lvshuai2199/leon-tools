/** 示例数据里的 Cursor 任务：按用户名分开，Key 不回给页面。 */
type Task = {
  id: string
  name: string
  agentStatus: string
  runStatus: string
  repoUrl: string
  updatedAt: string
  promptPreview: string
  startingRef: string
  resultText: string
  agentUrl: string
  prUrl: string
  branchName: string
}

type State = { hint: string; tasks: Task[] }

const byUser = new Map<string, State>()

function stateOf(username: string) {
  let s = byUser.get(username)
  if (!s) {
    s = { hint: '', tasks: [] }
    byUser.set(username, s)
  }
  return s
}

function now() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function card(t: Task) {
  return { id: t.id, name: t.name, agentStatus: t.agentStatus, runStatus: t.runStatus, repoUrl: t.repoUrl, updatedAt: t.updatedAt }
}

function board(s: State, warning = '') {
  return { configured: !!s.hint, hint: s.hint, warning, tasks: s.tasks.map(card) }
}

export function handleCursor(username: string, path: string, method: string, body: Record<string, any>) {
  const s = stateOf(username)
  const fail = (message: string) => ({ status: 200, body: { status: 500, message, data: null } })
  const ok = (data: unknown) => ({ status: 200, body: { status: 200, message: 'success', data } })

  if (path === '/app/cursor/board' && method === 'GET') return ok(board(s))
  if (path === '/app/cursor/key' && method === 'POST') {
    const key = String(body.apiKey || '').trim()
    if (key.length < 16) return fail('Key 格式不对。到 Cursor Dashboard → Integrations 创建一个 User API Key')
    s.hint = key.slice(-4)
    return ok(board(s))
  }
  if (path === '/app/cursor/key/clear' && method === 'POST') {
    s.hint = ''
    return ok(board(s))
  }
  if (path === '/app/cursor/usage' && method === 'GET') {
    if (!s.hint) return fail('先保存你自己的 Cursor API Key')
    return ok({ available: true, unlimited: false, remainingPercent: 62, resetAt: '2026-11-02T00:00:00.000Z', warning: '' })
  }
  if (path === '/app/cursor/repos' && method === 'GET') {
    if (!s.hint) return fail('先保存你自己的 Cursor API Key')
    return ok({
      warning: '',
      repos: [
        { owner: 'leon-lv', name: 'aubo-notes', url: 'https://github.com/leon-lv/aubo-notes' },
        { owner: 'leon-lv', name: 'cnc-station', url: 'https://github.com/leon-lv/cnc-station' },
        { owner: 'leon-lv', name: 'elite-notes', url: 'https://github.com/leon-lv/elite-notes' },
        { owner: 'leon-lv', name: 'leon-tools', url: 'https://github.com/leon-lv/leon-tools' },
      ],
    })
  }
  if (path === '/app/cursor/tasks' && method === 'POST') {
    if (!s.hint) return fail('先保存你自己的 Cursor API Key')
    const prompt = String(body.prompt || '').trim()
    const repo = String(body.repoUrl || '').trim()
    if (!prompt) return fail('先写要做什么')
    if (!/^https?:\/\//.test(repo)) return fail('仓库地址需要是 http(s) 链接')
    const id = `t${Date.now()}`
    const name = prompt.split('\n')[0].slice(0, 40) || '未命名任务'
    const task: Task = {
      id,
      name,
      agentStatus: 'IDLE',
      runStatus: 'FINISHED',
      repoUrl: repo,
      updatedAt: now(),
      promptPreview: prompt.replace(/\n/g, ' ').slice(0, 180),
      startingRef: String(body.startingRef || ''),
      resultText: `已收到：${prompt}`,
      agentUrl: 'https://cursor.com/agents',
      prUrl: '',
      branchName: 'cursor/demo',
    }
    s.tasks.unshift(task)
    return ok(task)
  }
  const one = /^\/app\/cursor\/tasks\/([^/]+)$/.exec(path)
  if (one && method === 'GET') {
    const task = s.tasks.find((t) => t.id === decodeURIComponent(one[1]))
    return task ? ok(task) : fail('任务不存在')
  }
  const follow = /^\/app\/cursor\/tasks\/([^/]+)\/follow$/.exec(path)
  if (follow && method === 'POST') {
    const task = s.tasks.find((t) => t.id === decodeURIComponent(follow[1]))
    if (!task) return fail('任务不存在')
    const prompt = String(body.prompt || '').trim()
    if (!prompt) return fail('先写要做什么')
    task.resultText = `${task.resultText}\n\n追加：${prompt}`
    task.runStatus = 'FINISHED'
    task.agentStatus = 'IDLE'
    task.updatedAt = now()
    return ok(task)
  }
  const cancel = /^\/app\/cursor\/tasks\/([^/]+)\/cancel$/.exec(path)
  if (cancel && method === 'POST') {
    const task = s.tasks.find((t) => t.id === decodeURIComponent(cancel[1]))
    if (!task) return fail('任务不存在')
    task.runStatus = 'CANCELLED'
    task.updatedAt = now()
    return ok(task)
  }
  return null
}
