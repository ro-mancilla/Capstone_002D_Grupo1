# Stage 1: Cache Gradle dependencies
FROM gradle:9.5.0-jdk21 AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home

WORKDIR /home/gradle/app

# Copy the full monorepo: settings.gradle.kts now includes every module (KMP app + backend),
# so Gradle needs the whole project graph present to configure even a single :backend:* task.
COPY --chown=gradle:gradle . .
# The image already ships JDK 21; drop the daemon-JVM criteria (JetBrains JDK via foojay) so the
# build never tries to auto-provision a toolchain over the network.
RUN rm -f gradle/gradle-daemon-jvm.properties

# Trigger dependency download for the app module
RUN gradle :backend:app:dependencies --no-daemon

# Stage 2: Build Application
FROM gradle:9.5.0-jdk21 AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN rm -f gradle/gradle-daemon-jvm.properties

# Build the fat JAR using the correct multi-module path
RUN gradle :backend:app:buildFatJar --no-daemon

# Stage 3: Create the Runtime Image
FROM eclipse-temurin:21-alpine AS runtime
EXPOSE 8080
WORKDIR /app
# Correct path for multi-module build output
COPY --from=build /home/gradle/src/backend/app/build/libs/*.jar /app/ktor-docker-sample.jar
ENTRYPOINT ["java","-jar","/app/ktor-docker-sample.jar"]

