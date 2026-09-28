# 管理端部署（Linux Nginx）

管理端 Vue3 打成静态文件，放到服务器 `/var/www/leonpro-admin`，站点通过 `/admin/` 访问。站点根目录 `/` 归用户端（`/var/www/leonpro-web`）。

nginx 配置由后端统一部署，本脚本**不改 nginx**，只上传管理端自己的文件。接口仍是浏览器请求 `/prod-api/...`，由 nginx 转到后端。

## 本机准备

1. Node ≥ 18，使用 pnpm。
2. 复制 `deploy.env.example` 为 `deploy.env`，填 `DEPLOY_PASSWORD` 或 `DEPLOY_SSH_KEY`。
3. 目标目录用 `DEPLOY_ADMIN_REMOTE_DIR`（默认 `/var/www/leonpro-admin`，必须在 `/var/www/` 下）。旧的 `DEPLOY_REMOTE_DIR`、`DEPLOY_NGINX_CONF`、`NGINX_RELOAD` 已不再使用。

## 一键部署

```powershell
cd personal-server/LeonPro_frontend/vue3_frontend
.\deploy\deploy.ps1
```

流程：`pnpm run build-only`，然后清空并上传 `dist/` 到 `DEPLOY_ADMIN_REMOTE_DIR`。

本机内存紧张时，脚本用 `NODE_OPTIONS=--max-old-space-size=1024` 打包。若仍失败，可先关掉占内存的程序再执行。

访问：`http://124.220.57.33/admin/`（登录页 `/admin/#/login`）

## 服务器上查看

```bash
ls /var/www/leonpro-admin
curl -I http://127.0.0.1/admin/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/prod-api/
```
