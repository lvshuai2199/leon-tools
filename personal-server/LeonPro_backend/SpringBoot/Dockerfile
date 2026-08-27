# 使用国内镜像源的 JDK 21
FROM docker.mirrors.ustc.edu.cn/library/openjdk:21-jdk-slim

WORKDIR /app

# 由 mvn package + deploy 脚本拷贝到服务器同目录的 app.jar
COPY app.jar app.jar

EXPOSE 8089

ENV SPRING_PROFILES_ACTIVE=prod

CMD ["java", "-jar", "app.jar"]
