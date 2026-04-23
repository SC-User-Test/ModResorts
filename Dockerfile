# Multi-stage Dockerfile for ModResorts Java EE Application
# Stage 1: Build the application
FROM maven:3.9.4-eclipse-temurin-8 AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code and WebContent
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with Tomcat
FROM tomcat:9.0-jre8

# Remove default Tomcat applications
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR file from builder stage
COPY --from=builder /workspace/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser /usr/local/tomcat

# Set environment variables for JVM tuning
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV CATALINA_OPTS="-Duser.timezone=UTC"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER appuser

# Start Tomcat
CMD ["catalina.sh", "run"]
