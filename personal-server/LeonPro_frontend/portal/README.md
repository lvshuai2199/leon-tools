# Portal · 个人空间公开展示端

只读、免登录的公开展示前端，与管理端 `vue3_frontend` 完全独立（无登录、无后台菜单、不携带任何 token）。
当前包含 **壁纸** 模块，后续可按模块注册表扩展（如笔记）。

- 技术栈：Vue 3 + Vite 5 + TypeScript + vue-router 4（history 模式，base `/portal/`）
- 无 UI 库，纯 CSS / scoped style
- 依赖：`vue`、`vue-router`、`vite`、`@vitejs/plugin-vue`、`typescript`、`vue-tsc`

## 本地开发

```bash
pnpm i
pnpm dev          # http://localhost:5174/portal/
```

### Mock 开关

| 文件 | 变量 | 说明 |
| --- | --- | --- |
| `.env.development` | `VITE_USE_MOCK=true` | 开发模式默认使用本地 Mock（10 个分组，其中「风景」50 张、「待整理」为空分组，图片为 picsum.photos 固定 seed 的绝对地址） |
| `.env.production` | `VITE_USE_MOCK=false` | 生产构建始终请求真实接口，Mock 代码不会打进产物 |

联调真实后端：把 `.env.development` 中的 `VITE_USE_MOCK` 改成 `false`，或新建 `.env.development.local` 覆盖；也可临时：

```bash
VITE_USE_MOCK=false pnpm dev
```

### 接口前缀 `VITE_API_PREFIX` 与后端地址 `VITE_API_TARGET`

| 变量 | 所在文件 | 值 | 作用 |
| --- | --- | --- | --- |
| `VITE_API_PREFIX` | `.env.development` | `/dev-api` | 接口与相对图片地址前缀（开发） |
| `VITE_API_PREFIX` | `.env.production` | `/prod-api` | 接口与相对图片地址前缀（线上，仅此路径反代到后端） |
| `VITE_API_TARGET` | `.env` | `http://localhost:8089` | dev / preview 代理目标 |

`VITE_API_PREFIX` 同时用于两处：

1. **接口请求**：`${VITE_API_PREFIX}/extern/wallpaper/groups`、`${VITE_API_PREFIX}/extern/wallpaper/images?...`
2. **相对图片地址**：后端列表返回的 `/uploads/wallpaper/...` 会被补成 `${VITE_API_PREFIX}/uploads/wallpaper/...`；
   已是 `http(s)://`（或 `//`）开头、或已带该前缀的地址保持不变（逻辑在 `src/api/http.ts` 的 `resolveAssetUrl()`）。

开发服务器把 `/dev-api` 代理到 `VITE_API_TARGET` 并去掉前缀（`/dev-api/extern/...` → `http://localhost:8089/extern/...`）。
`pnpm preview` 使用 production 模式，同理代理 `/prod-api`，可模拟线上。

```bash
VITE_API_TARGET=http://192.168.1.10:8089 VITE_USE_MOCK=false pnpm dev
```

## 构建

```bash
pnpm build        # vue-tsc --noEmit 类型检查 + vite build
pnpm preview      # 本地预览构建产物 http://localhost:4174/portal/
```

产物输出到 `dist/`，资源路径均以 `/portal/` 为前缀。

## Nginx 部署示例

线上已有 `/prod-api/` 反代到后端（接口与 `/uploads` 图片都经由它访问），因此只需新增一个 `/portal/` location。
将 `dist/` 内容上传到服务器，例如 `/usr/share/nginx/html/portal/`：

```nginx
# 公开展示端：/portal/
location /portal/ {
    alias /usr/share/nginx/html/portal/;
    index index.html;
    # history 模式回退
    try_files $uri $uri/ /portal/index.html;
}
```

> 无需额外配置 `/extern` 或图片 location。使用 `alias` 时 `try_files` 回退路径要写完整的 `/portal/index.html`。

## 接口约定（只读、公开）

| 接口（均带 `VITE_API_PREFIX` 前缀） | 说明 |
| --- | --- |
| `GET /extern/wallpaper/groups` | 公开分组 `{id, name, groupKey, description, sort, imageCount, isPublic, coverUrl, coverThumbUrl}` |
| `GET /extern/wallpaper/images?group=<groupKey>&current=<n>&size=<n>` | 分页图片 Page `{records, total, current, pages}`（`size` 可能缺省；请求 size 上限 100，本端用 24） |

- 统一返回包装按 `{status, message, data}` 处理，`status` 为 `200` 或 `0` 视为成功，失败时页面显示 `message`。如有变动，只需修改 `src/api/http.ts` 中的 `unwrap()`。
- id 为 32 位字符串；`isPublic` 为 0/1。
- 字段映射集中在 `src/api/wallpaper.ts` 的 `mapGroup / mapImage / mapPage`。
- 图片使用规则：首页壁纸卡片封面用 `coverThumbUrl`（缺省回退 `coverUrl`）；浏览网格只用 `thumbUrl`；只有全屏查看器与「下载原图」使用 `url`。
- **不调用** `/extern/wallpaper/random`，页面上不展示也不提供复制任何 token / 接口地址的功能。

## 目录结构

```
src/
├── api/
│   ├── http.ts            # fetch 封装、unwrap()、错误处理、resolveAssetUrl() 前缀补全
│   ├── types.ts           # 类型定义
│   └── wallpaper.ts       # 壁纸接口 + 字段映射（唯一入口）
├── mock/wallpaper.ts      # Mock 数据（模拟分页、延迟）
├── components/            # TopBar / ModuleCard / StateBlock
├── composables/           # useMediaQuery / useScrollLock
├── directives/lazy.ts     # v-lazy：IntersectionObserver 懒加载
├── views/
│   ├── HomeView.vue       # 首页模块卡片
│   └── wallpaper/
│       ├── WallpaperBrowse.vue
│       └── components/    # GroupTabs / GroupSidebar / ImageGrid / ImageViewer
├── config.ts              # 站点名称等常量
├── modules.ts             # 模块注册表
├── router.ts
└── main.ts
```

## 新增模块（例如笔记）

1. 在 `src/modules.ts` 的 `MODULES` 中追加一项：`{ key, name, description, icon, route, getCover, routes }`
2. 在 `src/views/notes/` 下编写页面
3. 首页卡片与路由会自动生成，无需改动其他文件

## 页面说明

- **首页**：56px 顶栏（左侧站点名，右侧预留插槽）+ 模块卡片网格（桌面 3 列 / 移动 1 列），卡片 16:9 封面；壁纸封面每次打开随机选取一个非空分组的封面缩略图。
- **壁纸浏览** `/wallpaper/:groupKey`：
  - 分组切换：横向 tabs（名称 + 数量）；分组数 > 8 且宽度 ≥ 1024px 时改为左侧 200px 分组列表，移动端保持横向滚动 tabs
  - 分组信息行：名称、描述、图片数量
  - 统一 16:9 网格（桌面 4 / 平板 3 / 移动 2 列，间距 12px、圆角 8px），悬停时底部标题上滑
  - 缩略图懒加载、无限滚动分页、空分组 / 到底 / 错误状态
  - 全屏查看器：`#111` 背景，左右按钮、键盘 ←/→/Esc、移动端左右滑动，到达已加载末尾自动加载下一页；底部显示标题、分辨率与「下载原图」
  - 选中分组保存在 URL 中，未指定时默认第一个分组
