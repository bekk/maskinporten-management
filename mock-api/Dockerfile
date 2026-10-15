# Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY . .
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/build/libs/mock-api-all.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
