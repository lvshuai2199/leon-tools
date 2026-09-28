/**
 * 用户端菜单（GET /auth/me 的 appMenus）归一化与权限判断。纯函数，node --test 可直接测。
 *
 * 后端返回格式目前两份文档写法不同，这里两种都认：
 *  A. 设计文档 6.4：{ id, path, name, parent, type: "dir"|"page", icon, sort, hidden, component }
 *  B. 接口文档第 5 条（和后台菜单一样）：{ id, menuName, menuUrl, parentId, sortOrder, icon, visible, menuType,
 *     permission, component, routeName, routeKey? }
 *     完整路径优先用 routeKey；否则 menuUrl 以 / 开头就直接用，不以 / 开头时拼上父级路径。
 * 统一输出 A 格式，按 sort 升序。
 */

const DIR_TYPES = new Set(["dir", "directory", "m", "catalog"]);

function str(v, d = "") {
  return v === undefined || v === null ? d : String(v);
}

function num(v, d = 0) {
  const n = typeof v === "string" ? Number(v) : v;
  return Number.isFinite(n) ? n : d;
}

function truthyFlag(v) {
  return v === true || v === 1 || v === "1" || v === "true";
}

function falsyFlag(v) {
  return v === false || v === 0 || v === "0" || v === "false";
}

function joinPath(parent, child) {
  const c = str(child).replace(/^\/+/, "");
  const p = str(parent).replace(/\/+$/, "");
  return `${p}/${c}` || "/";
}

/** @returns {import("../api/types").AppMenu[]} */
export function normalizeAppMenus(list) {
  if (!Array.isArray(list)) return [];
  const raws = list.filter((x) => x && typeof x === "object");
  const byId = new Map(raws.map((r) => [str(r.id), r]));
  const pathCache = new Map();

  function fullPath(r, depth = 0) {
    const id = str(r.id);
    if (id && pathCache.has(id)) return pathCache.get(id);
    let path = "";
    if (typeof r.path === "string" && r.path) path = r.path;
    else if (typeof r.routeKey === "string" && r.routeKey) path = r.routeKey;
    else {
      const url = str(r.menuUrl);
      const parent = r.parentId != null && r.parentId !== "" ? byId.get(str(r.parentId)) : undefined;
      if (url.startsWith("/") || !parent || depth > 10) path = url.startsWith("/") ? url : `/${url}`;
      else path = joinPath(fullPath(parent, depth + 1), url);
    }
    if (id) pathCache.set(id, path);
    return path;
  }

  function parentPath(r) {
    if (r.parent !== undefined) return r.parent ? str(r.parent) : null;
    if (r.parentId === undefined || r.parentId === null || r.parentId === "" || r.parentId === "0") return null;
    const p = byId.get(str(r.parentId));
    return p ? fullPath(p) : null;
  }

  const out = raws
    .map((r) => {
      const rawType = str(r.type ?? r.menuType, "page").toLowerCase();
      let hidden = false;
      if (r.hidden !== undefined) hidden = truthyFlag(r.hidden);
      else if (r.visible !== undefined) hidden = falsyFlag(r.visible);
      return {
        id: str(r.id),
        path: fullPath(r),
        name: str(r.name ?? r.menuName),
        parent: parentPath(r),
        type: DIR_TYPES.has(rawType) ? "dir" : "page",
        icon: str(r.icon),
        sort: num(r.sort ?? r.sortOrder),
        hidden,
        component: str(r.component),
      };
    })
    .filter((m) => m.path && m.path !== "/");
  return out.sort((a, b) => a.sort - b.sort);
}

/**
 * 某个菜单路径是否已授权。
 * 后端会把已授权页面的 hidden 后代一起返回；这里再按本地清单兜底一次：
 * 清单里 hidden 的页面，只要父级已授权就视为已授权。
 * @param {Array<{path:string}>} menus 归一化后的 appMenus
 * @param {string} menuPath 清单里的完整路径（如 /crab/:id）
 * @param {Array<{path:string,parent?:string|null,hidden?:boolean}>} [manifest] 本地 menus.json
 */
export function canAccessMenu(menus, menuPath, manifest = []) {
  const allowed = new Set((menus || []).map((m) => m.path));
  let path = menuPath;
  for (let i = 0; i < 10 && path; i++) {
    if (allowed.has(path)) return true;
    const item = manifest.find((m) => m.path === path);
    if (!item || !item.hidden || !item.parent) return false;
    path = item.parent;
  }
  return false;
}

/**
 * 过渡兼容（一期后端）：现在的 GET /auth/me 因 sys_menus 还没有 client 字段，appMenus 固定返回空数组，
 * 另给了 canUseCrab / canUseRegCode 两个布尔值。appMenus 为空且有这两个字段时，按本地清单拼出等价菜单：
 * canUseCrab → /crab 及其 hidden 子页；canUseRegCode → /regcode。
 * 判断依据完全来自后端返回，前端不写角色规则；后端按清单返回 appMenus 后这段自动不再生效（可删）。
 * @param {Record<string, unknown>} me GET /auth/me 返回（已去掉 appMenus 之外的包装）
 * @param {Array<{path:string,name:string,parent?:string|null,type:string,icon?:string,sort?:number,hidden?:boolean,component?:string}>} manifest
 */
export function menusFromLegacyFlags(me, manifest) {
  if (!me || typeof me !== "object") return [];
  const roots = [];
  if (me.canUseCrab === true) roots.push("/crab");
  if (me.canUseRegCode === true) roots.push("/regcode");
  if (!roots.length) return [];
  const under = (path) => {
    let p = path;
    for (let i = 0; i < 10 && p; i++) {
      if (roots.includes(p)) return true;
      p = (manifest.find((m) => m.path === p) || {}).parent || null;
    }
    return false;
  };
  return normalizeAppMenus(
    manifest
      .filter((m) => under(m.path))
      .map((m) => ({ id: `local:${m.path}`, path: m.path, name: m.name, parent: m.parent ?? null, type: m.type, icon: m.icon || "", sort: m.sort || 0, hidden: !!m.hidden, component: m.component || "" })),
  );
}

/** 首页工具卡：非 hidden 的页面，按 sort 排序 */
export function homeToolMenus(menus) {
  return (menus || []).filter((m) => m.type === "page" && !m.hidden).sort((a, b) => a.sort - b.sort);
}
