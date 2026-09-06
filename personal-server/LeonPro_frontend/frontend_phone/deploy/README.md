# uni-app H5 部署（Linux Nginx）

打成静态文件，放到服务器 `/var/www/leonpro-h5`，由已有 Nginx 提供 `/h5/`。接口走 `/prod-api/`，和 PC 端一样反代到本机 Java `127.0.0.1:8089`。

单独目录，避免 Vue3 部署清空 `/var/www/leonpro` 时把手机端冲掉。

## 本机准备

1. Node ≥ 18。
2. 复制 `deploy.env.example` 为 `deploy.env`。密码可留空，会复用后端 `deploy.env`。

## 一键部署

```powershell
cd personal-server/LeonPro_frontend/frontend_phone
.\deploy\deploy.ps1
```

流程：`npm run build:h5` → 上传 `dist/build/h5/` → 写入 Nginx 配置 → `nginx -t` && `reload`。

访问：`http://<服务器IP>/h5/`（当前新机示例：`http://124.220.57.33/h5/`）
