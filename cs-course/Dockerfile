# 构建上下文是仓库根目录（需要拿到 target 里的 jar）
# 用法：docker build -f cs-course/Dockerfile -t cs-course .
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 非 root 运行：容器逃逸时降低影响面
RUN addgroup -S app && adduser -S app -G app

COPY cs-course/target/cs-course-1.0.0.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8081

# MaxRAMPercentage：让 JVM 跟随容器内存限制，而不是读宿主机的
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
