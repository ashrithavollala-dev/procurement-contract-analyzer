# syntax=docker/dockerfile:1
# ---- Build stage: Maven + JDK 17 -------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
# Retry transient download failures (Maven 3.9 picks up MAVEN_ARGS automatically).
ENV MAVEN_ARGS="-Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=120000"
# Resolve dependencies in a cached layer that only changes when the pom changes.
# The BuildKit cache mount keeps ~/.m2 between builds, so a retry after a network failure
# does not re-download everything.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests need a Docker engine (Testcontainers) and run in CI / locally via `mvn verify`, not inside
# this image build. The image is built from the same sources that CI tests.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/contract-analyzer-backend-*.jar /build/app.jar

# ---- Runtime stage: JRE 17 on Ubuntu (glibc) ---------------------------------------------------
# Not Alpine: the ONNX Runtime and HuggingFace tokenizer native libraries need glibc.
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /data/uploads /data/onnx-cache /data/djl-cache \
    && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    UPLOAD_DIR=/data/uploads \
    EMBEDDING_CACHE_DIR=/data/onnx-cache \
    DJL_CACHE_DIR=/data/djl-cache \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"

USER app
EXPOSE 8080
# Liveness only: the app is healthy even when the LLM is down (that degrades features, not health).
HEALTHCHECK --interval=15s --timeout=5s --start-period=600s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
