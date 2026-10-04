# 构建当前前端，不把本机依赖或私有资料带入镜像。
FROM node:22-alpine AS frontend-builder
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend ./
RUN npm run build

# 构建包含当前前端的应用产物
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY --from=frontend-builder /frontend/dist ./frontend/dist

RUN mvn -B -DskipTests package

# 创建轻量运行时镜像
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=docker,deepseek,lightweight
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
