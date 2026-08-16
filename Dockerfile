# syntax=docker/dockerfile:1.7

###############################################################################
# Stage 1 — build
# Produce bootJar inside the image so the CI/Docker build is self-contained.
# Uses Temurin JDK 25 Alpine — smallest official image that ships JDK 25.
###############################################################################
FROM eclipse-temurin:25-jdk-alpine AS build
RUN apk add --no-cache bash git tzdata
WORKDIR /src

# Gradle wrapper + build files first for better layer caching.
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle gradle.properties ./

# Sources last — source changes should not bust the Gradle dependency cache.
COPY src/ src/
COPY tailwind.config.js ./

# No .git in this stage, so the build.gradle git-describe fallback would bake "dev".
# CI passes the release tag via --build-arg APP_VERSION=<tag>.
ARG APP_VERSION=dev
RUN chmod +x gradlew \
    && ./gradlew -Pversion=$APP_VERSION bootJar -x test --no-daemon

###############################################################################
# Stage 2 — runtime
# Minimal JRE 25 Alpine with a non-root user. Inherits the /ext plugin loader
# convention: drop plugin JARs under /ext and PropertiesLauncher picks them up.
###############################################################################
FROM eclipse-temurin:25-jre-alpine
# Fixed uid/gid 1000 so the data-volume chown note below is deterministic.
# busybox wget (bundled in Alpine) powers the HEALTHCHECK — no extra package needed.
RUN apk add --no-cache tzdata ca-certificates \
    && addgroup -S -g 1000 app && adduser -S -u 1000 -G app app \
    && mkdir -p /app /ext /allure \
    && chown -R app:app /app /ext /allure

# App data (H2 db + allure/results + allure/reports) is written under ./allure relative to
# the working dir, so run from /allure and mount the persistent volume there, otherwise the
# data lands on the ephemeral container layer and is lost on recreate.
# NOTE: a bind-mount/volume created by the old root-based image is owned by root:root; the
# non-root runtime user "app" (uid 1000, gid 1000) cannot write to it. Chown the host path
# to 1000:1000 before upgrading, e.g. `chown -R 1000:1000 ./allure-server-store`.
WORKDIR /allure
COPY --from=build --chown=app:app /src/build/libs/allure-server*.jar /app/allure-server.jar

USER app
ARG PORT=8080
EXPOSE ${PORT}
ENV JAVA_OPTS="-Xms256m -Xmx2048m"
# Actuator health endpoint (spring-boot-starter-actuator) gates orchestrator readiness.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Dloader.path=/ext -jar /app/allure-server.jar"]
