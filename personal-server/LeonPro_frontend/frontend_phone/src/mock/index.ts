/**
 * 开发模式示例数据（VITE_USE_MOCK）。只在 vite dev 下被动态加载，生产构建不会打包。
 * 返回真正的 Response，走和真实接口同一套解包、401/403 处理。
 *
 * 示例账号：
 *   任意用户名 + 6 位以上密码可登录（密码写 wrong123 模拟“用户名或密码错误”）
 *   用户名 client → 注册码子账号，只有「注册码生成」菜单
 *   用户名 nomenu → 没有任何用户端菜单
 *   用户名 legacy → 模拟一期后端：appMenus 为空数组，只给 canUseCrab / canUseRegCode
 */
import * as wp from './wallpaper'

type Body = Record<string, unknown> | unknown[] | null

const ok = (data: unknown) => ({ status: 200, message: 'success', data })
const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

function json(httpStatus: number, body: Body) {
  return new Response(JSON.stringify(body), { status: httpStatus, headers: { 'Content-Type': 'application/json' } })
}

/* ------------------------------ 账号 ------------------------------ */

const APP_MENUS = [
  { id: 'menu_app_crab', path: '/crab', name: '螃蟹出货', parent: null, type: 'page', icon: 'crab', sort: 1, hidden: false, component: 'crab/list' },
  { id: 'menu_app_crab_new', path: '/crab/new', name: '录入出货单', parent: '/crab', type: 'page', icon: '', sort: 2, hidden: true, component: 'crab/entry' },
  { id: 'menu_app_crab_detail', path: '/crab/:id', name: '出货单详情', parent: '/crab', type: 'page', icon: '', sort: 3, hidden: true, component: 'crab/edit' },
  { id: 'menu_app_regcode', path: '/regcode', name: '注册码生成', parent: null, type: 'page', icon: 'key', sort: 4, hidden: false, component: 'regcode/index' },
]

function userOf(username: string) {
  return {
    id: `u_${username}`,
    username,
    nickname: username === 'client' ? '客户子账号' : username === 'demo' ? '演示用户' : username,
    email: `${username}@example.com`,
    roleId: username === 'client' ? 'role_regcode_client' : 'role_user',
    roleName: username === 'client' ? '注册码客户' : '普通用户',
    parentId: username === 'client' ? 'u_demo' : null,
    menuIds: [],
  }
}

function regCodeOf(username: string) {
  if (username === 'client') return { isSubUser: false, canCreateSubUsers: true, canManageSubUsers: true, maxSubUsers: 3, createdCount: 1 }
  if (username === 'sub') return { isSubUser: true, canCreateSubUsers: false, canManageSubUsers: false, maxSubUsers: 0, createdCount: 0 }
  return { isSubUser: false, canCreateSubUsers: false, canManageSubUsers: true, maxSubUsers: 0, createdCount: 0 }
}

function menusOf(username: string) {
  if (username === 'nomenu') return []
  if (username === 'client') return APP_MENUS.filter((m) => m.path === '/regcode')
  return APP_MENUS
}

function tokenUser(init: RequestInit): string | null {
  const auth = (init.headers as Record<string, string> | undefined)?.Authorization || ''
  const m = /^Bearer mock-token-(.+)$/.exec(auth)
  return m ? decodeURIComponent(m[1]) : null
}

/* ------------------------------ 螃蟹出货 ------------------------------ */

function today(offset = 0) {
  const d = new Date(Date.now() + offset * 86400000)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

let crabSeq = 3
const crabs: Record<string, any>[] = [
  { id: 'c1', publicId: 'p_c1', shipDate: today(), seqNo: 1, customerName: '张三', phone: '13800000001', address: '广东省深圳市南山区科技园 1 号', spec: '4两公', quantity: 10, paid: 1, shipped: 0, trackingNo: '', remark: '' },
  { id: 'c2', publicId: 'p_c2', shipDate: today(), seqNo: 2, customerName: '李四', phone: '13800000002', address: '浙江省杭州市西湖区文三路 2 号', spec: '3两母', quantity: 6, paid: 0, shipped: 1, trackingNo: 'SF1234567890', remark: '' },
  { id: 'c3', publicId: 'p_c3', shipDate: today(-1), seqNo: 1, customerName: '王五', phone: '13800000003', address: '江苏省苏州市工业园区 3 号', spec: '5两公', quantity: 8, paid: 1, shipped: 1, trackingNo: 'YT0000000001', remark: '' },
]
const withShare = (c: Record<string, any>) => ({ ...c, sharePath: `/s/crab/${c.publicId}` })

/* ------------------------------ 路由 ------------------------------ */

export async function mockFetch(pathWithQuery: string, init: RequestInit): Promise<Response> {
  await delay(150 + Math.floor(Math.random() * 200))
  const url = new URL(pathWithQuery, 'http://mock.local')
  const path = url.pathname
  const q = url.searchParams
  const method = (init.method || 'GET').toUpperCase()
  const body = typeof init.body === 'string' && init.body ? JSON.parse(init.body) : {}

  // ---------- 公开 ----------
  if (path === '/auth/login' && method === 'POST') {
    const username = String(body.username || '').trim()
    if (!username || String(body.password || '') === 'wrong123') return json(200, { status: 500, message: '用户名或密码错误', data: null })
    return json(200, ok({ ...userOf(username), token: `mock-token-${encodeURIComponent(username)}` }))
  }
  if (path === '/public/wallpaper/groups') return json(200, ok(wp.groups()))
  if (path === '/public/wallpaper/images') {
    const page = wp.images(q.get('group') || '', Number(q.get('current') || 1), Number(q.get('size') || 24))
    if (!page) return json(404, { status: 404, message: '分组不存在或未公开', data: null })
    return json(200, ok(page))
  }
  const share = /^\/public\/crabShipment\/(.+)$/.exec(path)
  if (share) {
    const c = crabs.find((x) => x.publicId === decodeURIComponent(share[1]))
    return c ? json(200, ok(withShare(c))) : json(404, { status: 404, message: '出货单不存在', data: null })
  }

  // ---------- 需要登录 ----------
  const username = tokenUser(init)
  if (!username) return json(401, { status: 401, message: '登录已失效，请重新登录', data: null })

  if (path === '/auth/logout') return json(200, ok(null))
  if (path === '/auth/me' && method === 'GET') {
    const base = { ...userOf(username), root: false, canLoginWeb: false, regCode: regCodeOf(username) }
    if (username === 'legacy') return json(200, ok({ ...base, appMenus: [], canUseCrab: true, canUseRegCode: true }))
    const menus = menusOf(username)
    return json(200, ok({ ...base, canUseCrab: menus.some((m) => m.path === '/crab'), canUseRegCode: menus.some((m) => m.path === '/regcode'), appMenus: menus }))
  }
  if (path === '/auth/me' && method === 'POST') return json(200, ok(null))

  const allowed = new Set(menusOf(username).map((m) => m.path))
  const forbidden = () => json(403, { status: 403, message: '无权限访问', data: null })

  if (path.startsWith('/app/crabShipment')) {
    if (!allowed.has('/crab')) return forbidden()
    const sub = path.slice('/app/crabShipment'.length)
    if (sub === '/getAll') {
      // 参数和原手机端一致：shipDate 单日，或 shipDateStart/shipDateEnd 区间；customerName / phone 搜索
      const date = q.get('shipDate')
      const start = q.get('shipDateStart') || date
      const end = q.get('shipDateEnd') || date
      const name = (q.get('customerName') || '').trim()
      const phone = (q.get('phone') || '').trim()
      const list = crabs
        .filter((c) => (!start || c.shipDate >= start) && (!end || c.shipDate <= end))
        .filter((c) => (!name || c.customerName.includes(name)) && (!phone || c.phone.includes(phone)))
        .map(withShare)
      return json(200, ok({ records: list, total: list.length, current: 1, size: 200, pages: 1 }))
    }
    if (sub === '/save') {
      const i = crabs.findIndex((c) => c.id === body.id)
      if (i >= 0) crabs[i] = { ...crabs[i], ...body }
      else crabs.push({ ...body, id: `c${++crabSeq}`, publicId: `p_c${crabSeq}` })
      return json(200, ok(withShare(i >= 0 ? crabs[i] : crabs[crabs.length - 1])))
    }
    if (sub === '/status') {
      const c = crabs.find((x) => x.id === body.id)
      if (!c) return json(200, { status: 500, message: '出货单不存在', data: null })
      Object.assign(c, body)
      return json(200, ok(withShare(c)))
    }
    if (sub === '/batchSave') {
      const list = Array.isArray(body.records) ? body.records : []
      for (const item of list) crabs.push({ ...item, shipDate: item.shipDate || body.shipDate || today(), id: `c${++crabSeq}`, publicId: `p_c${crabSeq}` })
      return json(200, ok(list.length))
    }
    if (sub === '/parse') return json(200, ok({ records: [] }))
    if (sub === '/del') {
      const ids: string[] = Array.isArray(body) ? body : []
      for (const id of ids) {
        const i = crabs.findIndex((c) => c.id === id)
        if (i >= 0) crabs.splice(i, 1)
      }
      return json(200, ok(ids.length))
    }
    const one = /^\/([^/]+)$/.exec(sub)
    if (one && method === 'GET') {
      const c = crabs.find((x) => x.id === decodeURIComponent(one[1]))
      return c ? json(200, ok(withShare(c))) : json(404, { status: 404, message: '出货单不存在', data: null })
    }
  }

  if (path.startsWith('/common/')) {
    if (!allowed.has('/regcode')) return forbidden()
    if (path === '/common/regCodeConfig/list') {
      return json(200, ok([
        { id: 'cfg1', company: '示例科技', name: '基础版', componentName: 'Basic' },
        { id: 'cfg2', company: '示例科技', name: '专业版', componentName: 'Pro' },
        { id: 'cfg3', company: '演示公司', name: '标准版', componentName: 'Std' },
      ]))
    }
    if (path === '/common/regCodeUser/myQuota') {
      if (username !== 'client') return json(200, ok({ unlimited: true, items: [], generateLimit: 0, generateUsed: 0, remaining: 0 }))
      const items = [
        { configId: 'cfg1', configName: '基础版', allocated: 10, used: 3, remaining: 7 },
        { configId: 'cfg2', configName: '专业版', allocated: 5, used: 5, remaining: 0 },
        { configId: 'cfg3', configName: '标准版', allocated: 20, used: 2, remaining: 18 },
      ]
      return json(200, ok({ unlimited: false, items, generateLimit: 35, generateUsed: 10, remaining: 25 }))
    }
    if (path === '/common/regCode/subUsers' && method === 'GET') {
      if (username === 'sub') return json(403, { status: 403, message: '子用户不能再创建或管理子用户', data: null })
      return json(200, ok({
        createdCount: 1, maxSubUsers: 3, canCreate: true,
        items: [{ id: 's1', username: 'sub01', nickname: '门店一', status: 1, createTime: '2026-09-28 10:00:00', usedTotal: 2, allocatedTotal: 10 }],
      }))
    }
    if (path.startsWith('/common/regCode/subUsers')) {
      return json(200, { status: 500, message: '示例数据：子用户管理第二块再做', data: null })
    }
    if (path === '/common/regCode/genTempRegCode') {
      const code = String(body.regCode || '').toUpperCase()
      return json(200, ok({
        oneMonthValid: `${code}-1M-A1B2`,
        twoMonthValid: `${code}-2M-C3D4`,
        fourMonthValid: `${code}-4M-E5F6`,
        sixMonthValid: `${code}-6M-G7H8`,
        thirteenMonthValid: `${code}-13M-J9K0`,
        longTimeValid: `${code}-FOREVER`,
      }))
    }
  }

  return json(404, { status: 404, message: `示例数据里没有这个接口：${method} ${path}`, data: null })
}
