# Multi-stage Dockerfile for ModResorts Java WAR Application
# Base Image: amazoncorretto:8 (explicitly provided)
# Build Tool: Maven
# Package Type: WAR
# Target Platform: AWS EKS

# ============================================
# Stage 1: Builder - Build the WAR file
# ============================================
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy Maven configuration files first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code and web content
COPY src ./src
COPY WebContent ./WebContent

# Build the WAR file
RUN mvn clean package -DskipTests -B

# ============================================
# Stage 2: Runtime - Run the application
# ============================================
FROM amazoncorretto:8

# Install Tomcat 9 (compatible with Java EE 7)
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION=9.0.82

RUN yum install -y tar gzip && \
    mkdir -p "$CATALINA_HOME" && \
    curl -fsSL "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" | \
    tar -xz --strip-components=1 -C "$CATALINA_HOME" && \
    yum clean all && \
    rm -rf /var/cache/yum

# Create non-root user for security
RUN groupadd -r tomcat && useradd -r -g tomcat tomcat

# Copy WAR file from builder stage
COPY --from=builder /workspace/target/*.war $CATALINA_HOME/webapps/modresorts.war

# Set ownership
RUN chown -R tomcat:tomcat $CATALINA_HOME

# Switch to non-root user
USER tomcat

# Expose application port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Set timezone
ENV TZ=UTC

# Start Tomcat
CMD ["catalina.sh", "run"]
