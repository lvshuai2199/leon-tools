# 后端部署（Linux + Docker）

需要上线时在本机执行 `deploy.ps1`（Windows）或 `deploy.sh`（Git Bash）。不要改服务器上已有的 MySQL 容器。

新机器装 Nginx / Docker / `mysql8`：见 `personal-server/bootstrap/`。非 root（如 `ubuntu`）账号会通过 `sudo` 建目录和执行 `docker compose`。

## 服务器现状（2026-09-04）

| 组件 | 说明 |
| --- | --- |
| 主机 | `124.220.57.33`（用户 `ubuntu`），目录 `/opt/leonpro/backend` |
| MySQL | 已有容器 `mysql8`，映射 `3306`。Java 用宿主机网络连 `127.0.0.1:3306` |
| Redis | compose 里的 `leonpro-redis`，只绑 `127.0.0.1:6379` |
| Java | 容器 `leonpro-backend`，`network_mode: host`，端口 `8089` |
| 基础镜像 | `eclipse-temurin:21-jre-jammy`（走服务器 Docker 加速器，不要写死中科大源） |

生产配置：`application-prod.yml`，可用环境变量 `MYSQL_HOST` / `MYSQL_PASSWORD` / `REDIS_HOST` 覆盖。

## 本机准备

1. 复制 `deploy.env.example` 为 `deploy.env`，填 `DEPLOY_PASSWORD` 或 `DEPLOY_SSH_KEY`。
2. 保持 `DOCKER_UP=1`，上传后会在服务器执行 `docker compose up -d --build`。
3. 打包需要 **JDK 21**。`deploy.ps1` 会自动尝试 `%USERPROFILE%\.jdks\` 下的 21。Maven 使用项目内 `.mvn/settings.xml`（阿里云），避免公司 Nexus 拉不到 Spring Boot 4。

## 一键部署

```powershell
cd personal-server/LeonPro_backend/SpringBoot
.\deploy\deploy.ps1
```

流程：`mvn package` → 拷 `app.jar` 到 `deploy/dist/` → scp 到服务器 → `docker compose up -d --build`。MySQL 容器不动。

GitHub：仓库 Actions 里 `Deploy backend`，手动点 Run（需配 Secrets）。

## 服务器上查看

```bash
cd /opt/leonpro/backend
docker compose ps
docker compose logs -f leonpro-backend
```

接口：`http://<IP>:8089/`（根路径无页面，404 正常）。Web 走 Nginx 的 `/prod-api/`，见前端 `vue3_frontend/deploy`。

启动时：库里没有 `admin` 才插入一次（`admin` / `admin123`），已有账号不会覆盖。菜单同样只补缺失项，不每次改排序。

同机 `leonpro_db_dev` 的数据若要一次性拷到 `leonpro_db_prod`，用 `sql/sync_dev_routes_to_prod.sql`，不要每次部署都跑。
