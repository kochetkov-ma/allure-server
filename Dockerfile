FROM gradle:jdk11 as build
COPY . .
RUN gradle -Pversion=docker --no-daemon -PnodeVersion=12.16.3 vaadinPrepareNode bootJar

FROM openjdk:11-jre-slim as production
COPY --from=build /home/gradle/build/libs/allure-server-docker.jar /allure-server-docker.jar
# Set port
EXPOSE ${PORT:-8080}
# Run application
ENV JAVA_OPTS="-Xmx256m -Xmx2048m"
ENTRYPOINT ["java","-jar","-XX:+UseContainerSupport", "-Dspring.profiles.active=${PROFILE:default}","/allure-server-docker.jar"]