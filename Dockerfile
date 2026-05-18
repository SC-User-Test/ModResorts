# Multi-stage Dockerfile for ModResorts Java EE Web Application
# Stage 1: Build the application using Maven
FROM maven:3.9.4-eclipse-temurin-8 AS builder

# Set working directory
WORKDIR /workspace

# Copy the entire project structure (required for proper Maven build)
COPY . .

# Build the application (skip tests for faster builds)
# This generates the WAR file in target/ directory
RUN mvn clean package -DskipTests

# Stage 2: Runtime environment with Tomcat
FROM amazoncorretto:8

# Install Tomcat 9 (compatible with Java EE 7)
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION=9.0.82

# Create tomcat user for security
RUN yum install -y tar gzip && \
    groupadd -r tomcat && \
    useradd -r -g tomcat -d $CATALINA_HOME -s /sbin/nologin tomcat && \
    mkdir -p $CATALINA_HOME && \
    curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz | \
    tar -xz --strip-components=1 -C $CATALINA_HOME && \
    rm -rf $CATALINA_HOME/webapps/* && \
    chown -R tomcat:tomcat $CATALINA_HOME && \
    yum clean all && \
    rm -rf /var/cache/yum

# Copy the WAR file from builder stage
COPY --from=builder /workspace/target/*.war $CATALINA_HOME/webapps/ROOT.war

# Set ownership
RUN chown tomcat:tomcat $CATALINA_HOME/webapps/ROOT.war

# Configure JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER tomcat

# Start Tomcat
CMD ["catalina.sh", "run"]
