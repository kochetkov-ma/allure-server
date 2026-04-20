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

RUN chmod +x gradlew \
    && ./gradlew bootJar -x test --no-daemon

###############################################################################
# Stage 2 — runtime
# Minimal JRE 25 Alpine with a non-root user. Inherits the /ext plugin loader
# convention: drop plugin JARs under /ext and PropertiesLauncher picks them up.
###############################################################################
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache tzdata ca-certificates \
    && addgroup -S app && adduser -S app -G app \
    && mkdir -p /app /ext \
    && chown -R app:app /app /ext

WORKDIR /app
COPY --from=build --chown=app:app /src/build/libs/allure-server*.jar /app/allure-server.jar

USER app
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx2048m"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Dloader.path=/ext -jar /app/allure-server.jar"]
