# 使用官方镜像名，由服务器 Docker 加速器拉取
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 由 mvn package + deploy 脚本拷贝到服务器同目录的 app.jar
COPY app.jar app.jar

EXPOSE 8089

ENV SPRING_PROFILES_ACTIVE=prod

CMD ["java", "-jar", "app.jar"]
