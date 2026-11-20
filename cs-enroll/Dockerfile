# 用法：docker build -f cs-enroll/Dockerfile -t cs-enroll .
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY cs-enroll/target/cs-enroll-1.0.0.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
