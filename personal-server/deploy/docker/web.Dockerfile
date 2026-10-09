# 用户端静态文件。构建结果在 /out，由部署脚本拷到 /var/www/leonpro-web。
FROM node:22-bookworm AS build
ENV NODE_OPTIONS="--max-old-space-size=1024"
ENV CI=true
WORKDIR /src
COPY personal-server/LeonPro_frontend/shared /src/personal-server/LeonPro_frontend/shared
COPY personal-server/LeonPro_frontend/frontend_phone /src/personal-server/LeonPro_frontend/frontend_phone
WORKDIR /src/personal-server/LeonPro_frontend/frontend_phone
RUN npm ci && npm run build

FROM alpine:3.20
COPY --from=build /src/personal-server/LeonPro_frontend/frontend_phone/dist /out
