# Multi-stage Dockerfile for ModResorts Java EE Web Application
# Stage 1: Build the application using Maven
FROM maven:3.9.4-eclipse-temurin-8 AS builder

# Set working directory
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy the entire project source
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image with Tomcat
FROM tomcat:9.0-jre8-temurin-jammy

# Set maintainer label
LABEL maintainer="ModResorts Team"
LABEL application="modresorts"
LABEL version="2.0.0"

# Remove default Tomcat applications
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR file from builder stage to Tomcat webapps as ROOT.war
COPY --from=builder /workspace/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Create non-root user for security
RUN groupadd -r tomcat && useradd -r -g tomcat tomcat

# Create directories for logs and configuration
RUN mkdir -p /usr/local/tomcat/logs /usr/local/tomcat/conf && \
    chown -R tomcat:tomcat /usr/local/tomcat

# Set environment variables for JVM tuning
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV CATALINA_OPTS="-Duser.timezone=UTC"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER tomcat

# Health check using application's native health endpoint
# Note: Tomcat image includes curl, so we can use it for health checks
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Start Tomcat
CMD ["catalina.sh", "run"]
