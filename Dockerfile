FROM openjdk:21-slim
COPY build/libs/*.jar /allure-server-docker.jar
# Set port
EXPOSE ${PORT:-8080}
# Run application
ENV JAVA_OPTS="-Xms256m -Xmx2048m"
ENTRYPOINT ["java", "-Dloader.path=/ext", "-jar", "allure-server-docker.jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:default}"]
