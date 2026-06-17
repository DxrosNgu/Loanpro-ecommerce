# ── Stage 1: build the JAR ────────────────────────────────────────────────────
FROM gradle:8.7-jdk21 AS build
WORKDIR /app

# Cache dependency resolution layer separately from source compilation
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

# Now copy source and build
COPY src ./src
RUN gradle bootJar --no-daemon

# ── Stage 2: minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS serve
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
