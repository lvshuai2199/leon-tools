# LeonPro 新服务器一键初始化

在 **Ubuntu / Debian** 上安装 Nginx、Docker，并用 Docker 启动与现网一致的 `mysql8`（3306）。Redis 和 Java 后端仍由现有 `deploy.sh` / `deploy.ps1` 拉起，本脚本不装业务容器。

可重复执行：已安装的包会跳过，MySQL 数据卷 `/opt/leonpro/mysql/data` 不会删除。

当前生产机：`124.220.57.33`，用户 `ubuntu`（sudo）。

## 本机执行（推荐）

1. 复制 `env.example` 为 `bootstrap.env`，填写新机器 IP 和 `MYSQL_ROOT_PASSWORD`（与 `application-prod.yml` 一致，或之后改后端环境变量）。
2. Linux：

```bash
cd personal-server/bootstrap
bash bootstrap.sh
```

Windows PowerShell：

```powershell
cd personal-server/bootstrap
.\bootstrap.ps1
```

会把本目录上传到新机 `/tmp/leonpro-bootstrap`，用 `sudo` 执行 `bootstrap-server.sh`（适合 `ubuntu` 等非 root 账号）。

## 在服务器上手动执行

```bash
# 把 bootstrap 目录拷到服务器后
sudo bash /opt/leonpro/bootstrap/bootstrap-server.sh
```

同目录下如有 `bootstrap.env` 会自动读取。

## 装完之后

| 路径 / 容器 | 用途 |
| --- | --- |
| `/var/www/leonpro-web` | 用户端静态文件（站点根 `/`，frontend_phone 部署） |
| `/var/www/leonpro-admin` | 管理端静态文件（`/admin/`，vue3_frontend 部署） |
| `/opt/leonpro/backend` | 后端 jar / compose |
| `mysql8` | MySQL 8.0，默认只绑 `127.0.0.1:3306` |
| `leonpro_db_prod` / `leonpro_db_dev` | 已建库 |

从旧机迁库（本机）：

```powershell
cd personal-server/bootstrap
# 复制 migrate.env.example 为 migrate.env，填旧机 SSH
.\migrate-db.ps1
```

手工迁库：

```bash
# 旧机
docker exec mysql8 mysqldump -uroot -p --single-transaction --databases leonpro_db_prod leonpro_db_dev > dump.sql

# 新机
docker exec -i mysql8 mysql -uroot -p < dump.sql
```

然后在本机分别跑前端、后端 `deploy.sh` / `deploy.ps1`。

需要公网连 3306 时，把 `MYSQL_PUBLISH` 改成 `0.0.0.0:3306:3306` 后重跑脚本（仍建议只走 SSH 隧道）。

## Nginx 站点配置（唯一一份）

`nginx-leonpro.conf` 是 LeonPro 站点配置的唯一来源，服务器上装在 `/etc/nginx/sites-available/default`。前端部署脚本只上传静态文件，不改 nginx；原来的 `vue3_frontend/deploy/nginx.conf` 已删除。

| 路径 | 内容 |
| --- | --- |
| `/` | 用户端 `/var/www/leonpro-web`，history 路由，未知路径回落 `index.html`（含 `/s/crab/{publicId}`） |
| `/admin/` | 管理端 `/var/www/leonpro-admin`，hash 路由，不回落；`/admin` 301 到 `/admin/` |
| `/prod-api/` | 反代 `127.0.0.1:8089`，去掉前缀；图片走 `/prod-api/uploads/...` |
| `/trace`、`/cnc/` | 与原来一致 |

缓存：两个 `index.html` 为 `no-cache`；打包产物（用户端 `/assets/`，管理端 `/admin/js|css|img|fonts|media/`）一年 `immutable`，缺文件直接 404。

更新配置（只由站点负责人执行）：

```powershell
cd personal-server/bootstrap
.\deploy-nginx.ps1 -Check     # 上传并在服务器 nginx -t，然后还原，不 reload（线上不受影响）
.\deploy-nginx.ps1            # 正式安装：备份 → 写入 → nginx -t（失败自动还原）→ reload（失败自动还原）→ 冒烟
.\deploy-nginx.ps1 -Rollback  # 还原最近一次备份并 reload
```

Git Bash 用 `bash deploy-nginx.sh [--check|--force|--rollback [备份文件]|--yes]`。服务器端逻辑在 `nginx-install.sh`（也可以在服务器上 `sudo bash nginx-install.sh ...` 直接用）。

- 备份在服务器 `/etc/nginx/leonpro-backups/default.<时间戳>`。
- 以下情况会停下、不做任何修改，确认后加 `-Force` / `--force`：线上配置有 HTTPS（`listen 443` / `ssl_certificate`）而新配置没有；线上有新配置里没有的 location（`/h5`、`/portal` 属于计划内去掉的除外）；`server_name` 不同；`/var/www/leonpro-web` 或 `/var/www/leonpro-admin` 还没有 `index.html`（应先部署前端）。
- 登录信息读本目录 `bootstrap.env`，缺的再读后端 `deploy/deploy.env`。
