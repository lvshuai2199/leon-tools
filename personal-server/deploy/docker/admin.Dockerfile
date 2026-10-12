# 管理端静态文件。构建结果在 /out，由部署脚本拷到 /var/www/leonpro-admin。
FROM node:22-bookworm AS build
ENV NODE_OPTIONS="--max-old-space-size=1024"
ENV CI=true
WORKDIR /src
COPY personal-server/LeonPro_frontend/shared /src/personal-server/LeonPro_frontend/shared
COPY personal-server/LeonPro_frontend/elite-task /src/personal-server/LeonPro_frontend/elite-task
COPY personal-server/LeonPro_frontend/vue3_frontend /src/personal-server/LeonPro_frontend/vue3_frontend
WORKDIR /src/personal-server/LeonPro_frontend/vue3_frontend
# 仓库里的 pnpm-workspace.yaml 是本机 pnpm 11 配置，写了 Windows 的 storeDir，
# 也没有 packages 字段。锁文件没有入库。镜像里改成普通安装。
RUN printf '%s\n' 'packages:' '  - "."' 'nodeLinker: hoisted' > pnpm-workspace.yaml \
    && npm install -g pnpm@9.15.9 \
    && pnpm install \
    && pnpm run build-only

FROM alpine:3.20
COPY --from=build /src/personal-server/LeonPro_frontend/vue3_frontend/dist /out
