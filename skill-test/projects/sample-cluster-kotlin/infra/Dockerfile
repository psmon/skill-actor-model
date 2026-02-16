# Stage 1: Build
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/sample-cluster-kotlin/ ./
EXPOSE 25520
ENTRYPOINT ["./bin/sample-cluster-kotlin"]
