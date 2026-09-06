# LeonPro 新服务器一键初始化

在 **Ubuntu / Debian** 上安装 Nginx、Docker，并用 Docker 启动与现网一致的 `mysql8`（3306）。Redis 和 Java 后端仍由现有 `deploy.ps1` 拉起，本脚本不装业务容器。

可重复执行：已安装的包会跳过，MySQL 数据卷 `/opt/leonpro/mysql/data` 不会删除。

## 本机执行（推荐）

1. 复制 `env.example` 为 `bootstrap.env`，填写新机器 IP 和 `MYSQL_ROOT_PASSWORD`（与 `application-prod.yml` 一致，或之后改后端环境变量）。
2. PowerShell：

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
| `/var/www/leonpro` | Web 前端静态文件 |
| `/var/www/leonpro-h5` | uni-app H5（`/h5/`） |
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

然后在本机分别跑前端、后端 `deploy.ps1`。

需要公网连 3306 时，把 `MYSQL_PUBLISH` 改成 `0.0.0.0:3306:3306` 后重跑脚本（仍建议只走 SSH 隧道）。
