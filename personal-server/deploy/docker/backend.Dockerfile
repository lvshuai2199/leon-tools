# 在服务器上直接构建后端。上下文是仓库根目录。
# 运行镜像和现有 compose 一致：/app/app.jar，uploads、data 由卷挂进来。
FROM maven:3.9.9-eclipse-temurin-21 AS build
ENV MAVEN_OPTS="-Xmx768m"
WORKDIR /src
COPY personal-server/LeonPro_backend/SpringBoot /src/personal-server/LeonPro_backend/SpringBoot
COPY personal-server/LeonPro_frontend/vue3_frontend/src/router/menus.json /src/personal-server/LeonPro_frontend/vue3_frontend/src/router/menus.json
COPY personal-server/LeonPro_frontend/frontend_phone/src/router/menus.json /src/personal-server/LeonPro_frontend/frontend_phone/src/router/menus.json
WORKDIR /src/personal-server/LeonPro_backend/SpringBoot
RUN mvn -B -s .mvn/settings.xml -DskipTests package \
    && test -f deploy/dist/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/personal-server/LeonPro_backend/SpringBoot/deploy/dist/app.jar app.jar
EXPOSE 8089
ENV SPRING_PROFILES_ACTIVE=prod
CMD ["java", "-jar", "app.jar"]
