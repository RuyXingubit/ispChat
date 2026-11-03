# Stage 1: Build com Java 25 LTS e Gradle 9
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime enxuto e seguro (non-root) em Java 25 LTS
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S ispchat && adduser -S ispchat -G ispchat
USER ispchat:ispchat

COPY --from=builder /workspace/build/libs/*.jar /app/ispchat-node.jar

EXPOSE 443
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-jar", "/app/ispchat-node.jar"]
