# ── Stage 1: Build ──────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Gradle 래퍼 및 설정 먼저 복사 (레이어 캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Windows CRLF 줄바꿈 처리 + 실행 권한
RUN apk add --no-cache dos2unix && dos2unix gradlew && chmod +x gradlew

# 의존성 먼저 다운로드 (소스 변경 시 이 레이어는 캐시됨)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# 소스 복사 후 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# ── Stage 2: Run ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
