# 前端部署（Linux Nginx）

Vue3 打成静态文件，放到服务器 `/var/www/leonpro`，用**已有 Nginx** 提供页面，并把 `/prod-api/` 反代到本机 Java `127.0.0.1:8089`。不另起前端 Docker。

保留原有 `/trace` → `/var/www/tool/createTrace.html`。旧站点 `/var/www/web` 不再作为默认首页。

## 本机准备

1. Node ≥ 18，使用 pnpm。
2. 复制 `deploy.env.example` 为 `deploy.env`，填 `DEPLOY_PASSWORD` 或 `DEPLOY_SSH_KEY`。

## 一键部署

```powershell
cd personal-server/LeonPro_frontend/vue3_frontend
.\deploy\deploy.ps1
```

流程：`pnpm run build-only` → 上传 `dist/` → 写入 Nginx 配置 → `nginx -t` && `reload`。

本机内存紧张时，脚本用 `NODE_OPTIONS=--max-old-space-size=1024` 打包。若仍失败，可先关掉占内存的程序再执行。

访问：`http://124.220.57.33/`  （登录页 `/#/login`）  
接口：浏览器请求 `/prod-api/...`，Nginx 转到 `8089`。

非 root 账号（如 `ubuntu`）会通过 `sudo` 写 Nginx 配置并 reload。

## 服务器上查看

```bash
ls /var/www/leonpro
nginx -t
curl -I http://127.0.0.1/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/prod-api/
```
