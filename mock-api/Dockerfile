# Build stage. The mock is a subproject of the root build, so the build context is the repository root.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY . .
RUN ./gradlew :mock-api:buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/mock-api/build/libs/mock-api-all.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
