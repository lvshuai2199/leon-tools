/**
 * 开发模式示例数据（VITE_USE_MOCK）。只在 vite dev 下被动态加载，生产构建不会打包。
 * 返回真正的 Response，走和真实接口同一套解包、401/403 处理。
 *
 * 示例账号：
 *   任意用户名 + 6 位以上密码可登录（密码写 wrong123 模拟“用户名或密码错误”）
 *   账号只靠 appMenus 区分（和后端一致，不再有 canUseCrab / canUseRegCode）：
 *   demo 等其他用户名 → 出货 + 注册码都有
 *   用户名 crabonly → 只有螃蟹出货
 *   用户名 nomenu → 没有任何用户端菜单（首页看不到工具，直接进 /crab 被拒）
 *   用户名 empty  → 螃蟹出货没有任何记录（空状态）
 *   注册码各种账号（client / full / over / sub / noconfig / zero / newbie）见 mock/regcode.ts，只有「注册码生成」菜单
 */
import * as wp from './wallpaper'
import { REGCODE_ONLY_USERS, handleRegCode, regCodeMe } from './regcode'

type Body = Record<string, unknown> | unknown[] | null

const ok = (data: unknown) => ({ status: 200, message: 'success', data })
const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

function json(httpStatus: number, body: Body) {
  return new Response(JSON.stringify(body), { status: httpStatus, headers: { 'Content-Type': 'application/json' } })
}

/* ------------------------------ 账号 ------------------------------ */

/** 用户端菜单：字段和后台菜单一致（appMenus 原样返回，前端 utils/app-menus.js 按 menuUrl 对应 menus.json） */
const MENU_CRAB = { id: 101, menuName: '螃蟹出货', menuUrl: '/crab', parentId: 0, sortOrder: 1, icon: 'crab', visible: 1, menuType: 'C', permission: 'app:crab:list', component: 'crab/list', routeName: 'crabList' }
const MENU_CRAB_NEW = { id: 102, menuName: '录入出货单', menuUrl: '/crab/new', parentId: 101, sortOrder: 2, icon: '', visible: 0, menuType: 'C', permission: 'app:crab:add', component: 'crab/entry', routeName: 'crabNew' }
const MENU_CRAB_DETAIL = { id: 103, menuName: '出货单详情', menuUrl: '/crab/:id', parentId: 101, sortOrder: 3, icon: '', visible: 0, menuType: 'C', permission: 'app:crab:edit', component: 'crab/edit', routeName: 'crabDetail' }
const MENU_REGCODE = { id: 104, menuName: '注册码生成', menuUrl: '/regcode', parentId: 0, sortOrder: 4, icon: 'key', visible: 1, menuType: 'C', permission: 'app:regcode:gen', component: 'regcode/index', routeName: 'regcode' }
const CRAB_MENUS = [MENU_CRAB, MENU_CRAB_NEW, MENU_CRAB_DETAIL]

const NICKNAMES: Record<string, string> = {
  demo: '演示用户',
  client: '示例客户',
  full: '建满的客户',
  over: '超限的客户',
  sub: '门店子账号',
  noconfig: '未分配配置',
  zero: '不能建子用户的客户',
  newbie: '新客户',
}

function userOf(username: string) {
  const regcodeOnly = REGCODE_ONLY_USERS.has(username)
  return {
    id: `u_${username}`,
    username,
    nickname: NICKNAMES[username] || username,
    email: `${username}@example.com`,
    roleId: regcodeOnly ? 'role_regcode_client' : 'role_user',
    roleName: regcodeOnly ? '注册码客户' : '普通用户',
    parentId: username === 'sub' ? 'u_client' : regcodeOnly ? 'u_demo' : null,
    menuIds: [],
  }
}

/** 账号靠 appMenus 区分：都有（demo 等）/ 只有出货（crabonly）/ 只有注册码（注册码示例账号）/ 都没有（nomenu） */
function menusOf(username: string) {
  if (username === 'nomenu') return []
  if (username === 'crabonly') return CRAB_MENUS
  if (REGCODE_ONLY_USERS.has(username)) return [MENU_REGCODE]
  return [...CRAB_MENUS, MENU_REGCODE]
}

/** 当前账号能用的菜单路径（mock 接口按它返回 403） */
function allowedPaths(username: string) {
  return new Set(menusOf(username).map((m) => m.menuUrl))
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

let crabSeq = 5
const crabs: Record<string, any>[] = [
  { id: 'c1', publicId: 'p_c1', shipDate: today(), seqNo: 1, customerName: '张三', phone: '13800000001', address: '广东省深圳市南山区科技园 1 号', spec: '4两公', quantity: 10, paid: 1, shipped: 0, trackingNo: '', remark: '' },
  { id: 'c2', publicId: 'p_c2', shipDate: today(), seqNo: 2, customerName: '李四', phone: '13800000002', address: '浙江省杭州市西湖区文三路 2 号', spec: '3两母', quantity: 6, paid: 0, shipped: 1, trackingNo: 'SF1234567890', remark: '' },
  { id: 'c4', publicId: 'p_c4', shipDate: today(), seqNo: 3, customerName: '赵六', phone: '13800000004', address: '上海市浦东新区世纪大道 100 号 2 栋 1203 室', spec: '4两母', quantity: 12, paid: 1, shipped: 1, trackingNo: 'ZTO7788990011', remark: '' },
  { id: 'c5', publicId: 'p_c5', shipDate: today(), seqNo: 4, customerName: '孙七', phone: '', address: '北京市朝阳区望京街 8 号', spec: '5两公', quantity: 4, paid: 0, shipped: 0, trackingNo: '', remark: '' },
  { id: 'c3', publicId: 'p_c3', shipDate: today(-1), seqNo: 1, customerName: '王五', phone: '13800000003', address: '江苏省苏州市工业园区 3 号', spec: '5两公', quantity: 8, paid: 1, shipped: 1, trackingNo: 'YT0000000001', remark: '' },
]
const withShare = (c: Record<string, any>) => ({ ...c, sharePath: `/s/crab/${c.publicId}` })
/** 分享页：手机号中间打码（后端做的事，这里模拟） */
const masked = (c: Record<string, any>) => {
  const { id: _id, ...rest } = withShare(c) as Record<string, any>
  return { ...rest, phone: c.phone ? String(c.phone).replace(/^(\d{3})\d{4}(\d+)$/, '$1****$2') : '' }
}

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
    return c ? json(200, ok(masked(c))) : json(404, { status: 404, message: '出货单不存在', data: null })
  }

  // ---------- 需要登录 ----------
  const username = tokenUser(init)
  if (!username) return json(401, { status: 401, message: '登录已失效，请重新登录', data: null })

  if (path === '/auth/logout') return json(200, ok(null))
  if (path === '/auth/me' && method === 'GET') {
    const base = { ...userOf(username), root: false, canLoginWeb: false, regCode: regCodeMe(username) }
    return json(200, ok({ ...base, appMenus: menusOf(username) }))
  }
  if (path === '/auth/me' && method === 'POST') return json(200, ok(null))

  const allowed = allowedPaths(username)
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
      const list = (username === 'empty' ? [] : crabs)
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
    const r = handleRegCode(username, path, method, body)
    if (r) return json(r.status, r.body as Body)
  }

  return json(404, { status: 404, message: `示例数据里没有这个接口：${method} ${path}`, data: null })
}
