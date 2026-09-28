# LeonPro 用户端（frontend_phone）

原手机端 H5 和原展示端 portal 合并后的用户端：Vue 3 + TypeScript（allowJs 过渡）+ Vite 6 + Element Plus（按需引入），
电脑、手机两套布局，部署在站点根路径 `/`，路由用 history 模式。

> 目录名保持 `frontend_phone` 不变：后端 pom 会从 `src/router/menus.json` 打包用户端菜单清单。

## 常用命令

```powershell
npm install
npm run dev          # 开发，端口 5173，连后端（默认本机 8089）
npm run dev:mock     # 开发，全部接口用示例数据，不需要后端
npm run type-check   # vue-tsc 类型检查
npm test             # 纯函数单元测试（node --test）
npm run build        # prebuild 检查 menus.json → 类型检查 → 构建到 dist/
```

## 环境变量

| 文件 | 变量 | 说明 |
| --- | --- | --- |
| （环境变量 / `.env.local`） | `VITE_API_TARGET` | 开发代理转发到的后端地址，默认 `http://127.0.0.1:8089`；启动台传入的 `VITE_APP_API_URL` / `UNI_API_URL` 也认 |
| `.env.development` | `VITE_API_PREFIX=/dev-api` | 开发接口前缀，vite 代理去掉前缀后转给后端 |
| `.env.development` | `VITE_USE_MOCK=false` | 示例数据开关：`false` 全走后端（默认）；`true` 全部用示例数据；也可写逗号分隔的接口前缀（如 `/public/wallpaper,/auth`）只让这些接口用示例数据。只在开发模式生效，生产构建不会打包示例数据 |
| `.env.mock` | `VITE_USE_MOCK=true` | `npm run dev:mock` 用：不需要后端，全部接口走示例数据 |
| `.env.production` | `VITE_API_PREFIX=/prod-api` | 线上接口前缀（nginx 去掉前缀转后端） |

临时切换不用改文件：新建 `.env.development.local`（`*.local` 不进 git）覆盖 `VITE_USE_MOCK`，或直接 `npm run dev:mock`。

示例账号（示例数据模式）：任意用户名 + 6 位以上密码；密码 `wrong123` 模拟密码错误；
用户名 `client` 只有「注册码生成」菜单；`nomenu` 没有任何工具菜单；`legacy` 模拟现在的一期后端（appMenus 为空，只给 canUseCrab / canUseRegCode）。

## 目录

```
src/
  main.ts / App.vue          入口；App 按路由 meta 和断点选 BlankLayout / MobileLayout / DesktopLayout
  config.ts                  站点名、接口前缀、示例数据开关
  api/request.ts             唯一的请求层：Bearer token；401=登录失效（清状态、提示一次、跳登录）；403=只提示没有权限
  api/*.ts                   按新接口路径表写的接口（auth / crab / regcode / wallpaper）
  mock/                      开发示例数据（返回真正的 Response，走同一套解包和 401/403 处理）
  stores/user.ts             登录状态、token、appMenus（localStorage 键 lp_user，旧 userInfo 自动迁移）
  router/index.ts            路由表 + 守卫；公开页写在代码里，授权页来自 menus.json
  router/menus.json          用户端菜单清单（client=app，path 写完整路径，parent 写上级完整路径）
  layouts/                   DesktopLayout（顶栏）、MobileLayout（44px 标题栏 + 50px 标签栏）、BlankLayout
  composables/useBreakpoint  断点：<768 手机；768–1199 电脑无侧栏；≥1200 完整电脑
  components/                StateBlock（空/不存在/出错/加载）、ConfirmDrawer（手机底部确认框）、ToolIcon
  pages/                     home、login、wallpaper（Desktop/Mobile + useWallpaperBrowse）、crab、regcode、not-found
  utils/                     crab-parse / crab-ocr / barcode-scan / crab-ship-sheet（原手机端能力）、app-menus、ui
```

## 路由

`/` 首页（公开）、`/login`、`/wallpaper`、`/wallpaper/:groupKey`（公开）、`/crab?date=` 或 `?start=&end=`、
`/crab/new?date=`、`/crab/:id`、`/s/crab/:publicId`（公开分享）、`/regcode`，其他路径显示 404。

路由不能以 `/trace`、`/cnc`、`/admin`、`/uploads`、`/prod-api` 开头（同域名下已被其它服务占用），
`npm run build` 的 prebuild 会用 `../shared/scripts/check-menus.mjs` 检查清单。

## 主题

`vite.config.ts` 用 `additionalData` 给每个 scss 注入 `@use "@shared/theme" as lp;`（`../shared/theme.scss`），
全局样式 `src/styles/global.scss` 里 `@include lp.css-vars;` 一次；手机布局根节点和 `html.lp-mobile` 上 `@include lp.mobile-vars;`，
并配 `ElConfigProvider size="large"`（控件 44px）。

## 部署

`deploy/deploy.ps1` 仍是旧的 `/h5` 部署脚本，改成根路径部署要和 nginx 一起调整（history 模式需要把未知路径回落到 `index.html`），这一步还没做。
