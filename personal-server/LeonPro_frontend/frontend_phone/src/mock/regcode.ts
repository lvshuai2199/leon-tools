/**
 * 注册码示例数据（有状态：新建、分配、停用、启用都会改内存里的数据，刷新页面恢复）。
 *
 * 示例账号（密码任意 6 位以上）：
 *   client   顶层客户，最多 3 个子用户，已有 3 个：门店一（正常）、门店二（次数用完）、门店三（已停用）
 *   full     顶层客户，已建满（3 / 3）
 *   over     顶层客户，管理员把上限调小了（已建 5 / 最多 3）
 *   sub      客户建的子用户（只有生成页，没有标签页）
 *   noconfig 有注册码权限但一个配置都没分配（「暂无可用配置」）
 *   zero     顶层客户，上限 0、一个没建过（没有标签页）
 *   newbie   顶层客户，上限 3、还没建过子用户（子用户页空状态）
 *   其他账号 当作 ROOT：不限次数，上限 0（没有标签页）
 */

type Quota = Record<string, { allocated: number; used: number }>
interface SubUser {
  id: string
  username: string
  nickname: string
  status: number
  createTime: string
  quota: Quota
}
interface Account {
  maxSubUsers: number
  isSubUser?: boolean
  quota: Quota | null // null = 不限次数
  subs: SubUser[]
  configIds?: string[] // 只能看到这些配置（默认全部）
}

export const CONFIGS = [
  { id: 'cfg1', company: '示例科技', name: '基础版', componentName: 'Basic' },
  { id: 'cfg2', company: '示例科技', name: '专业版', componentName: 'Pro' },
  { id: 'cfg3', company: '演示公司', name: '标准版', componentName: 'Std' },
]
const cfgName = (id: string) => CONFIGS.find((c) => c.id === id)?.name || id

let subSeq = 100
function sub(username: string, nickname: string, status: number, quota: Quota, day = 20): SubUser {
  return { id: `s${++subSeq}`, username, nickname, status, createTime: `2026-09-${String(day).padStart(2, '0')} 10:${String(subSeq % 60).padStart(2, '0')}:00`, quota }
}

const accounts: Record<string, Account> = {
  client: {
    maxSubUsers: 3,
    quota: { cfg1: { allocated: 20, used: 3 }, cfg2: { allocated: 5, used: 5 }, cfg3: { allocated: 20, used: 2 } },
    subs: [
      sub('shop01', '门店一', 1, { cfg1: { allocated: 10, used: 2 }, cfg3: { allocated: 40, used: 10 } }, 21),
      sub('shop02', '门店二', 1, { cfg1: { allocated: 5, used: 5 } }, 22),
      sub('shop03', '门店三', 0, { cfg1: { allocated: 3, used: 3 } }, 23),
    ],
  },
  full: {
    maxSubUsers: 3,
    quota: { cfg1: { allocated: 30, used: 8 }, cfg3: { allocated: 10, used: 0 } },
    subs: [
      sub('full01', '分店 A', 1, { cfg1: { allocated: 10, used: 4 } }, 18),
      sub('full02', '分店 B', 1, { cfg1: { allocated: 6, used: 1 } }, 19),
      sub('full03', '分店 C', 1, { cfg1: { allocated: 3, used: 3 } }, 20),
    ],
  },
  over: {
    maxSubUsers: 3,
    quota: { cfg1: { allocated: 50, used: 20 } },
    subs: ['一', '二', '三', '四', '五'].map((n, i) => sub(`over0${i + 1}`, `网点${n}`, 1, { cfg1: { allocated: 6, used: i + 1 } }, 10 + i)),
  },
  sub: { maxSubUsers: 0, isSubUser: true, quota: { cfg1: { allocated: 10, used: 2 }, cfg3: { allocated: 5, used: 5 } }, subs: [], configIds: ['cfg1', 'cfg3'] },
  noconfig: { maxSubUsers: 0, quota: {}, subs: [], configIds: [] },
  newbie: { maxSubUsers: 3, quota: { cfg1: { allocated: 20, used: 0 }, cfg2: { allocated: 8, used: 2 } }, subs: [] },
  zero: { maxSubUsers: 0, quota: { cfg1: { allocated: 10, used: 1 }, cfg2: { allocated: 10, used: 0 } }, subs: [] },
}

export const REGCODE_ONLY_USERS = new Set(Object.keys(accounts))

const acc = (u: string): Account => accounts[u] || { maxSubUsers: 0, quota: null, subs: [] }
const enabledCount = (a: Account) => a.subs.filter((s) => s.status === 1).length
const remaining = (q: { allocated: number; used: number } | undefined) => (q ? Math.max(0, q.allocated - q.used) : 0)

type Res = { status: number; body: unknown }
const ok = (data: unknown): Res => ({ status: 200, body: { status: 200, message: 'success', data } })
const biz = (message: string): Res => ({ status: 200, body: { status: 500, message, data: null } })
const forbid = (message: string): Res => ({ status: 403, body: { status: 403, message, data: null } })

export function regCodeMe(username: string) {
  const a = acc(username)
  const created = enabledCount(a)
  return {
    isSubUser: !!a.isSubUser,
    canManageSubUsers: !a.isSubUser,
    canCreateSubUsers: !a.isSubUser && created < a.maxSubUsers,
    maxSubUsers: a.maxSubUsers,
    createdCount: created,
  }
}

function quotaItems(q: Quota) {
  return Object.entries(q).map(([configId, v]) => ({ configId, configName: cfgName(configId), allocated: v.allocated, used: v.used, remaining: remaining(v) }))
}

function subQuota(a: Account, s: SubUser) {
  const items = quotaItems(s.quota)
  return {
    subUserId: s.id,
    items,
    refundableTotal: items.reduce((n, i) => n + i.remaining, 0),
    creatorRemaining: a.quota ? quotaItems(a.quota) : [],
  }
}

function listOf(a: Account) {
  const created = enabledCount(a)
  return {
    createdCount: created,
    maxSubUsers: a.maxSubUsers,
    canCreate: created < a.maxSubUsers,
    items: a.subs.map((s) => {
      const items = quotaItems(s.quota)
      return {
        id: s.id,
        username: s.username,
        nickname: s.nickname,
        status: s.status,
        createTime: s.createTime,
        usedTotal: items.reduce((n, i) => n + i.used, 0),
        allocatedTotal: items.reduce((n, i) => n + i.allocated, 0),
      }
    }),
  }
}

function pwd() {
  const chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  return Array.from({ length: 10 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

/** 处理 /common/* ；不认识的返回 null */
export function handleRegCode(username: string, path: string, method: string, body: any): Res | null {
  const a = acc(username)

  if (path === '/common/regCodeConfig/list') {
    return ok(a.configIds ? CONFIGS.filter((c) => a.configIds!.includes(c.id)) : CONFIGS)
  }
  if (path === '/common/regCodeUser/myQuota') {
    if (!a.quota) return ok({ unlimited: true, items: [], generateLimit: 0, generateUsed: 0, remaining: 0 })
    const items = quotaItems(a.quota)
    const limit = items.reduce((n, i) => n + i.allocated, 0)
    const used = items.reduce((n, i) => n + i.used, 0)
    return ok({ unlimited: false, items, generateLimit: limit, generateUsed: used, remaining: limit - used })
  }
  if (path === '/common/regCode/genTempRegCode') {
    if (a.quota) {
      if (!Object.keys(a.quota).length) return biz('暂无可用配置，请联系管理员分配')
      const q = a.quota[String(body.configId)]
      if (!q || remaining(q) <= 0) return biz('该配置的生成次数已用完')
      q.used += 1
    }
    const code = String(body.regCode || '').toUpperCase()
    return ok({
      oneMonthValid: `${code}-1M-A1B2`,
      twoMonthValid: `${code}-2M-C3D4`,
      fourMonthValid: `${code}-4M-E5F6`,
      sixMonthValid: `${code}-6M-G7H8`,
      thirteenMonthValid: `${code}-13M-J9K0`,
      longTimeValid: `${code}-FOREVER-Z1`,
    })
  }

  if (!path.startsWith('/common/regCode/subUsers')) return null
  if (a.isSubUser) return forbid('子用户不能再创建或管理子用户')

  if (path === '/common/regCode/subUsers' && method === 'GET') return ok(listOf(a))
  if (path === '/common/regCode/subUsers' && method === 'POST') {
    const name = String(body.username || '').trim()
    if (name.length < 3 || name.length > 20) return biz('用户名需要 3–20 位')
    if (String(body.password || '').length < 6) return biz('密码至少 6 位')
    const taken = Object.values(accounts).some((x) => x.subs.some((s) => s.username === name)) || name in accounts
    if (taken) return biz('用户名已存在')
    if (enabledCount(a) >= a.maxSubUsers) return biz('子用户数量已达上限，请联系管理员')
    const quotas: Array<{ configId: string; count: number }> = Array.isArray(body.quotas) ? body.quotas : []
    for (const q of quotas) {
      if (a.quota && remaining(a.quota[q.configId]) < q.count) return biz(`「${cfgName(q.configId)}」剩余次数不足`)
    }
    const s = sub(name, String(body.nickname || ''), 1, {}, 29)
    for (const q of quotas) {
      if (!q.count) continue
      if (a.quota) a.quota[q.configId].allocated -= q.count
      s.quota[q.configId] = { allocated: q.count, used: 0 }
    }
    a.subs.push(s)
    return ok({ id: s.id, username: s.username })
  }

  const m = /^\/common\/regCode\/subUsers\/([^/]+)\/(quota|status|resetPassword)$/.exec(path)
  if (!m) return null
  const s = a.subs.find((x) => x.id === decodeURIComponent(m[1]))
  if (!s) return forbid('只能管理自己创建的子用户')
  const action = m[2]

  if (action === 'quota' && method === 'GET') return ok(subQuota(a, s))
  if (action === 'quota' && method === 'POST') {
    const items: Array<{ configId: string; delta: number }> = Array.isArray(body.items) ? body.items : []
    for (const it of items) {
      if (it.delta > 0 && a.quota && remaining(a.quota[it.configId]) < it.delta) return biz(`「${cfgName(it.configId)}」你的剩余次数不足`)
      if (it.delta < 0 && remaining(s.quota[it.configId]) < -it.delta) return biz(`「${cfgName(it.configId)}」只能收回未用的次数`)
    }
    for (const it of items) {
      if (!s.quota[it.configId]) s.quota[it.configId] = { allocated: 0, used: 0 }
      s.quota[it.configId].allocated += it.delta
      if (a.quota) {
        if (!a.quota[it.configId]) a.quota[it.configId] = { allocated: 0, used: 0 }
        a.quota[it.configId].allocated -= it.delta
      }
    }
    return ok(subQuota(a, s))
  }
  if (action === 'status') {
    const next = Number(body.status) === 1 ? 1 : 0
    if (next === 1) {
      if (s.status === 1) return ok({ status: 1, refunded: [], refundedTotal: 0 })
      if (enabledCount(a) >= a.maxSubUsers) return biz('启用中的子用户已达上限，不能再启用')
      s.status = 1
      return ok({ status: 1, refunded: [], refundedTotal: 0 })
    }
    const refunded: Array<{ configId: string; count: number }> = []
    for (const [configId, q] of Object.entries(s.quota)) {
      const n = remaining(q)
      if (!n) continue
      q.allocated = q.used
      if (a.quota?.[configId]) a.quota[configId].allocated += n
      refunded.push({ configId, count: n })
    }
    s.status = 0
    return ok({ status: 0, refunded, refundedTotal: refunded.reduce((n, r) => n + r.count, 0) })
  }
  if (action === 'resetPassword') return ok({ password: pwd() })
  return null
}
