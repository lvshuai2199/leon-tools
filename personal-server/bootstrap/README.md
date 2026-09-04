# LeonPro 新服务器一键初始化

在 **Ubuntu / Debian** 上安装 Nginx、Docker，并用 Docker 启动与现网一致的 `mysql8`（3306）。Redis 和 Java 后端仍由现有 `deploy.sh` / `deploy.ps1` 拉起。

可重复执行：已安装的包会跳过，MySQL 数据卷 `/opt/leonpro/mysql/data` 不会删除。

当前生产机：`124.220.57.33`，用户 `ubuntu`（sudo）。

## 本机执行

1. 复制 `env.example` 为 `bootstrap.env`，填写 IP 和 `MYSQL_ROOT_PASSWORD`。
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

会把本目录上传到 `/tmp/leonpro-bootstrap`，用 `sudo` 执行 `bootstrap-server.sh`。

## 装完之后

| 路径 / 容器 | 用途 |
| --- | --- |
| `/var/www/leonpro` | 前端静态文件 |
| `/opt/leonpro/backend` | 后端 jar / compose |
| `mysql8` | MySQL 8.0，默认只绑 `127.0.0.1:3306` |
| `leonpro_db_prod` / `leonpro_db_dev` | 已建库 |

从旧机迁库：

```bash
# 旧机
docker exec mysql8 mysqldump -uroot -p --single-transaction --databases leonpro_db_prod leonpro_db_dev > dump.sql

# 新机
docker exec -i mysql8 mysql -uroot -p < dump.sql
```

然后在本机分别跑前端、后端 `deploy.sh` / `deploy.ps1`。
