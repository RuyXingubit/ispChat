# Stage 1: Build multi-modulo com Java 25 LTS e Gradle 9
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY ispchat-common/build.gradle.kts ispchat-common/build.gradle.kts
COPY ispchat-node/build.gradle.kts ispchat-node/build.gradle.kts
COPY ispchat-principal/build.gradle.kts ispchat-principal/build.gradle.kts
COPY ispchat-erp-bridge/build.gradle.kts ispchat-erp-bridge/build.gradle.kts
RUN chmod +x gradlew && ./gradlew :ispchat-erp-bridge:dependencies --no-daemon

COPY ispchat-common/src/ ispchat-common/src/
COPY ispchat-erp-bridge/src/ ispchat-erp-bridge/src/
RUN ./gradlew :ispchat-erp-bridge:bootJar -x test --no-daemon

# Stage 2: Runtime enxuto e seguro (non-root) em Java 25 LTS na LAN do Provedor (8090)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S ispchat && adduser -S ispchat -G ispchat
USER ispchat:ispchat

COPY --from=builder /workspace/ispchat-erp-bridge/build/libs/ispchat-erp-bridge.jar /app/ispchat-erp-bridge.jar

EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-jar", "/app/ispchat-erp-bridge.jar"]
