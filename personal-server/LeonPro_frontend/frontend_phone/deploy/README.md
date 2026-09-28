# 用户端部署（frontend_phone → /var/www/leonpro-web）

Vue 3 + Vite 静态文件。脚本只做一件事：构建后把 `dist/` 上传到服务器 `/var/www/leonpro-web`（先清空再解压）。

- 不再部署到 `/h5/`，也不再写入或重载 Nginx 配置；Nginx 站点（根路径、history 回退到 `index.html`、`/prod-api/` 反代到 Java）由服务器侧统一维护。
- 接口前缀仍是 `/prod-api/`（见 `.env.production`）。
- 构建前会跑 `prebuild`（`scripts/check-menus.mjs` 检查 `src/router/menus.json` 与页面文件一致），不通过则不会上传。

## 本机准备

1. Node ≥ 18。
2. 复制 `deploy.env.example` 为 `deploy.env`，填写 `DEPLOY_HOST` / `DEPLOY_USER`。密码可留空，会依次复用 `bootstrap/bootstrap.env`、后端 `deploy.env` 里的登录方式。
3. 旧的 `DEPLOY_REMOTE_DIR`、`DEPLOY_NGINX_CONF`、`NGINX_RELOAD` 不再使用，留着也只会打印一条提示。

## 一键部署

```powershell
cd personal-server/LeonPro_frontend/frontend_phone
.\deploy\deploy.ps1
```

流程：`npm run build` → 打包 `dist/` → 上传到 `/tmp/leonpro-web.tar` → 清空并解压到 `/var/www/leonpro-web`。

`SKIP_BUILD=1`（或环境变量 `HUB_SKIP_BUILD=1`）时跳过构建，直接上传已有 `dist/`。
