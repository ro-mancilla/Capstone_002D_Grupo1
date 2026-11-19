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

# Trigger dependency download for the worker module
RUN gradle :backend:worker:dependencies --no-daemon

# Stage 2: Build Worker
FROM gradle:9.5.0-jdk21 AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN rm -f gradle/gradle-daemon-jvm.properties

# Build the worker using installDist
RUN gradle :backend:worker:installDist --no-daemon

# Stage 3: Create the Runtime Image
FROM eclipse-temurin:21-alpine AS runtime
WORKDIR /app
# Copy the installed distribution
COPY --from=build /home/gradle/src/backend/worker/build/install/worker /app/worker
ENTRYPOINT ["/app/worker/bin/worker"]
