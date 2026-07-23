# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# gradle wrapper/설정만 먼저 복사해 의존성 레이어를 캐싱(소스만 바뀌면 재다운로드 안 함)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r globelog && useradd -r -g globelog globelog \
    && mkdir -p /data/uploads && chown -R globelog:globelog /data/uploads

COPY --from=build /workspace/build/libs/*.jar app.jar

USER globelog
EXPOSE 15790
ENTRYPOINT ["java", "-jar", "/app/app.jar"]