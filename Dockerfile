# 阶段 1：构建应用产物
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# 阶段 2：轻量运行时镜像
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=docker,stub
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
