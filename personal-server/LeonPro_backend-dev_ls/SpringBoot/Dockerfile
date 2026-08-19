# 使用国内镜像源的 JDK 21
FROM docker.mirrors.ustc.edu.cn/library/openjdk:21-jdk-slim

# 设置工作目录
WORKDIR /app

# 将构建的 JAR 文件复制到镜像中
COPY SpringBoot-0.0.1-SNAPSHOT.jar myapp.jar

# 暴露应用程序的端口
EXPOSE 8089

# 指定应用程序的启动命令
CMD ["java", "-jar", "myapp.jar"]
