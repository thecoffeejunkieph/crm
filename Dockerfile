# syntax=docker/dockerfile:1

FROM gradle:8.14.3-jdk21 AS build
WORKDIR /home/gradle/project

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

# A JRE plus Chromium for PDF generation (this image ships Java 25; the app targets 21); the tag must match the playwright version in build.gradle.
FROM mcr.microsoft.com/playwright/java:v1.64.0-noble
WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
