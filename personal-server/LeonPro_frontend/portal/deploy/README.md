# 展示端（Portal）部署

构建产物放到服务器 `/var/www/leonpro-portal`，由宿主机 Nginx 在 `/portal/` 下提供。接口和图片都走已有的 `/prod-api/`，不需要额外的 location。

Nginx 站点配置只有一份：`LeonPro_frontend/vue3_frontend/deploy/nginx.conf`，里面已经有 `/portal/` 这一段。管理端、H5、展示端三个 deploy.ps1 都会上传这同一份配置，所以改 Nginx 只改那一个文件。

## 首次准备

1. Node ≥ 18，pnpm。
2. 复制 `deploy.env.example` 为 `deploy.env`。密码留空时会自动读 `bootstrap/bootstrap.env`。

## 一键部署

```powershell
cd personal-server/LeonPro_frontend/portal
.\deploy\deploy.ps1
```

流程：`pnpm run build`（类型检查 + 打包）→ 上传 `dist/` 到 `/var/www/leonpro-portal` → 写入 Nginx 配置 → `nginx -t` && `reload`。

访问：`http://124.220.57.33/portal/`

## 服务器上检查

```bash
ls /var/www/leonpro-portal
curl -I http://127.0.0.1/portal/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/prod-api/extern/wallpaper/groups
```
