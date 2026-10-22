# Build stage: compile the Spring Boot jar with the Gradle wrapper.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY backend/build.gradle.kts backend/
COPY simulator/build.gradle.kts simulator/
RUN ./gradlew :backend:dependencies --no-daemon -q > /dev/null
COPY backend/src backend/src
RUN ./gradlew :backend:bootJar --no-daemon -x test

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/backend/build/libs/route-dispatch-backend.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
