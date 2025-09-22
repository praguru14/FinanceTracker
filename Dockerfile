# ===== Build Stage =====
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies (better cache usage)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the app
RUN mvn clean package -DskipTests

# ===== Runtime Stage =====
FROM eclipse-temurin:17-jdk-jammy

# Set working directory
WORKDIR /app

# Copy the jar from builder stage
COPY --from=builder /app/target/FT-0.0.1-SNAPSHOT.jar app.jar

# Expose port (default Spring Boot is 8080)
EXPOSE 8090

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
