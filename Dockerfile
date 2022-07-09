FROM gradle:6.9.2-jdk11 as build
COPY . .
ARG RELEASE_VERSION=${RELEASE_VERSION:-0.0.0}
RUN gradle -Pversion=docker -i -s --no-daemon bootJar

FROM openjdk:11.0.15-jre-slim-bullseye as production
COPY --from=build /home/gradle/build/libs/allure-server-docker.jar /allure-server-docker.jar
# Set port
EXPOSE ${PORT:-8080}
# Run application
ENV JAVA_OPTS="-Xms256m -Xmx2048m"
ENTRYPOINT ["java", "-Dloader.path=/ext", "-cp", "allure-server-docker.jar", "-Dspring.profiles.active=${PROFILE:default}", "org.springframework.boot.loader.PropertiesLauncher"]