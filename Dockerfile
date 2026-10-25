# Multi-stage Dockerfile for Scalable SIS Spring Boot Backend

# Stage 1: Build the Maven application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper & POM
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml pom.xml

# Ensure mvnw has execute permissions
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production JAR
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S sis && adduser -S sis -G sis
USER sis:sis

# Copy fat JAR from builder
COPY --from=builder /app/target/sis-backend-0.0.1-SNAPSHOT.jar app.jar

# Render supplies PORT env variable (e.g. 10000 or 8085)
ENV PORT=8085
EXPOSE 8085

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
