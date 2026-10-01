#!/usr/bin/env node
// LeonPro 页面清单检查脚本：管理端和用户端共用，挂在各自 package.json 的 prebuild 上。
// 任何一条不通过就以退出码 1 结束，构建随之失败。
//
// 用法（在前端项目根目录执行）：
//   node ../shared/scripts/check-menus.mjs --client admin --menus src/router/menus.json --pages src/views
//   node ../shared/scripts/check-menus.mjs --client app   --menus src/router/menus.json --pages src/pages
//
// component 的两种写法（都是相对 --pages 目录，不带 .vue）：
//   1. 单文件：   "tool/wallpaper/index"  -> <pages>/tool/wallpaper/index.vue
//   2. 目录：     "crab/list"            -> <pages>/crab/list/ 目录，必须有 index.vue；
//                 Desktop.vue 和 Mobile.vue 要么都有，要么都没有。
//   同名的 .vue 文件和目录同时存在时算写法冲突，报错。
//   --no-dir 可以禁止目录写法（管理端的路由加载只认单文件时使用）。

import { existsSync, readFileSync, statSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FIELD_TYPES = {
  path: "string",
  name: "string",
  parent: "string|null",
  component: "string",
  icon: "string",
  sort: "number",
  type: "string",
  hidden: "boolean",
  keepAlive: "boolean",
  client: "string",
  redirect: "string",
  routeName: "string",
  alwaysShow: "boolean",
};
const REQUIRED = ["path", "name", "type", "client"];
const CLIENTS = ["admin", "app"];
// 用户端在站点根目录，下面这些路径前缀已被其它服务占用
const APP_RESERVED_PREFIXES = ["/trace", "/cnc", "/admin", "/uploads", "/prod-api"];

function parseArgs(argv) {
  const args = { dir: true };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--no-dir") args.dir = false;
    else if (a === "--client" || a === "--menus" || a === "--pages") args[a.slice(2)] = argv[++i];
    else if (a === "-h" || a === "--help") args.help = true;
    else throw new Error(`不认识的参数：${a}`);
  }
  return args;
}

function typeOf(v) {
  if (v === null) return "null";
  if (Array.isArray(v)) return "array";
  return typeof v;
}

function normalizePath(p) {
  return p.replace(/\/{2,}/g, "/").replace(/(.)\/$/, "$1").toLowerCase();
}

function isFile(p) {
  return existsSync(p) && statSync(p).isFile();
}
function isDir(p) {
  return existsSync(p) && statSync(p).isDirectory();
}

export function checkMenus({ client, menus, pages, dir = true, cwd = process.cwd() }) {
  const errors = [];
  const warnings = [];

  if (!CLIENTS.includes(client)) {
    errors.push(`--client 必须是 ${CLIENTS.join(" 或 ")}，现在是 ${client ?? "(空)"}`);
    return { errors, warnings, count: 0 };
  }
  const menusFile = resolve(cwd, menus ?? "src/router/menus.json");
  const pagesDir = resolve(cwd, pages ?? (client === "admin" ? "src/views" : "src/pages"));

  if (!isFile(menusFile)) {
    errors.push(`找不到清单文件：${menusFile}`);
    return { errors, warnings, count: 0 };
  }
  if (!isDir(pagesDir)) {
    errors.push(`找不到页面目录：${pagesDir}`);
    return { errors, warnings, count: 0 };
  }

  let list;
  try {
    list = JSON.parse(readFileSync(menusFile, "utf8").replace(/^\uFEFF/, ""));
  } catch (e) {
    errors.push(`清单不是合法的 JSON：${e.message}`);
    return { errors, warnings, count: 0 };
  }
  if (!Array.isArray(list)) {
    errors.push("清单最外层必须是数组");
    return { errors, warnings, count: 0 };
  }

  const byKey = new Map();
  const routeNames = new Map();

  list.forEach((item, idx) => {
    const at = `第 ${idx + 1} 项${item && typeof item.path === "string" ? `（${item.path}）` : ""}`;
    if (typeOf(item) !== "object") {
      errors.push(`${at}：必须是对象`);
      return;
    }
    for (const f of REQUIRED) {
      if (item[f] === undefined || item[f] === null || item[f] === "") errors.push(`${at}：缺少必填字段 ${f}`);
    }
    for (const [k, v] of Object.entries(item)) {
      const want = FIELD_TYPES[k];
      if (!want) {
        warnings.push(`${at}：未知字段 ${k}，后端不会处理`);
        continue;
      }
      if (v === undefined) continue;
      if (!want.split("|").includes(typeOf(v))) errors.push(`${at}：字段 ${k} 应为 ${want}，实际是 ${typeOf(v)}`);
    }

    if (typeof item.path === "string") {
      if (!item.path.startsWith("/")) errors.push(`${at}：path 必须以 / 开头，写完整路径`);
      const key = normalizePath(item.path);
      if (byKey.has(key)) errors.push(`${at}：path 与第 ${byKey.get(key).idx + 1} 项重复（不区分大小写）`);
      else byKey.set(key, { item, idx });
      if (client === "app") {
        const hit = APP_RESERVED_PREFIXES.find((p) => key === p || key.startsWith(p + "/"));
        if (hit) errors.push(`${at}：用户端路径不能以 ${hit} 开头（已被其它服务占用）`);
      }
    }
    if (item.type !== undefined && item.type !== "dir" && item.type !== "page") {
      errors.push(`${at}：type 只能是 dir 或 page`);
    }
    if (item.client !== undefined && item.client !== client) {
      errors.push(`${at}：client 是 ${item.client}，但这个清单属于 ${client}`);
    }
    if (typeof item.routeName === "string") {
      if (routeNames.has(item.routeName)) {
        warnings.push(`${at}：routeName ${item.routeName} 与第 ${routeNames.get(item.routeName) + 1} 项重复`);
      } else routeNames.set(item.routeName, idx);
    }

    // component
    if (item.type === "dir") {
      if (item.component && item.component !== "Layout") errors.push(`${at}：目录的 component 只能留空或写 Layout`);
    } else if (item.type === "page") {
      const c = item.component;
      if (typeof c !== "string" || c === "") {
        errors.push(`${at}：页面必须写 component`);
      } else if (c.startsWith("/") || c.includes("..") || c.includes("\\") || c.endsWith(".vue")) {
        errors.push(`${at}：component 写相对页面目录的路径，用 / 分隔，不带 .vue，不能以 / 开头或含 ..`);
      } else {
        const asFile = resolve(pagesDir, `${c}.vue`);
        const asDir = resolve(pagesDir, c);
        const fileOk = isFile(asFile);
        const dirOk = isDir(asDir);
        if (fileOk && dirOk) {
          errors.push(`${at}：${c}.vue 和 ${c}/ 目录同时存在，component 指向不明确`);
        } else if (fileOk) {
          // 单文件写法，通过
        } else if (dirOk) {
          if (!dir) {
            errors.push(`${at}：这个端不支持目录写法，component 请指向具体的 .vue 文件（例如 ${c}/index）`);
          } else {
            if (!isFile(resolve(asDir, "index.vue"))) errors.push(`${at}：目录 ${c}/ 里缺少 index.vue`);
            const d = isFile(resolve(asDir, "Desktop.vue"));
            const m = isFile(resolve(asDir, "Mobile.vue"));
            if (d !== m) {
              errors.push(`${at}：目录 ${c}/ 里 Desktop.vue 和 Mobile.vue 要么都有，要么都没有（现在只有 ${d ? "Desktop.vue" : "Mobile.vue"}）`);
            }
          }
        } else {
          errors.push(`${at}：找不到页面组件 ${c}.vue${dir ? ` 或目录 ${c}/` : ""}`);
        }
      }
    }
  });

  // parent：必须指向本文件里的条目，且不能成环
  for (const { item, idx } of byKey.values()) {
    if (item.parent === undefined || item.parent === null) continue;
    if (typeof item.parent !== "string") continue;
    const at = `第 ${idx + 1} 项（${item.path}）`;
    if (!byKey.has(normalizePath(item.parent))) {
      errors.push(`${at}：parent ${item.parent} 在清单里不存在`);
      continue;
    }
    const seen = new Set([normalizePath(item.path)]);
    let cur = byKey.get(normalizePath(item.parent));
    while (cur) {
      const k = normalizePath(cur.item.path);
      if (seen.has(k)) {
        errors.push(`${at}：parent 关系成环`);
        break;
      }
      seen.add(k);
      if (typeof cur.item.parent !== "string") break;
      cur = byKey.get(normalizePath(cur.item.parent));
    }
  }

  return { errors, warnings, count: list.length };
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  let args;
  try {
    args = parseArgs(process.argv.slice(2));
  } catch (e) {
    console.error(`[check-menus] ${e.message}`);
    process.exit(1);
  }
  if (args.help) {
    console.log("用法：node check-menus.mjs --client admin|app [--menus src/router/menus.json] [--pages src/views] [--no-dir]");
    process.exit(0);
  }
  const { errors, warnings, count } = checkMenus(args);
  for (const w of warnings) console.warn(`[check-menus] 提醒：${w}`);
  if (errors.length) {
    for (const e of errors) console.error(`[check-menus] 错误：${e}`);
    console.error(`[check-menus] ${args.client} 清单检查未通过：${errors.length} 个错误`);
    process.exit(1);
  }
  console.log(`[check-menus] ${args.client} 清单检查通过，共 ${count} 项`);
}
