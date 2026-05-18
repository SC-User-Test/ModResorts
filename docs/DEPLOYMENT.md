# ModResorts Application - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Task Definition Explained](#ecs-task-definition-explained)
7. [ECS Service Configuration](#ecs-service-configuration)
8. [Deployment to AWS ECS Fargate](#deployment-to-aws-ecs-fargate)
9. [Monitoring and Logging](#monitoring-and-logging)
10. [Troubleshooting](#troubleshooting)
11. [Scaling and Management](#scaling-and-management)
12. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Java EE 7 web application packaged as a WAR file, running on Apache Tomcat 9 in a containerized environment. This guide provides comprehensive instructions for deploying the application to AWS ECS Fargate.

**Application Details:**
- **Technology Stack**: Java 8, Java EE 7, Maven
- **Application Server**: Apache Tomcat 9
- **Package Type**: WAR
- **Application Port**: 8080
- **Health Check Endpoint**: `/health` and `/actuator/health`
- **Target Platform**: AWS ECS Fargate

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 1.29 or higher
- **AWS CLI**: Version 2.x
- **Maven**: Version 3.6 or higher (for local builds)
- **Java**: JDK 8 or higher (for local development)
- **Git**: For version control

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (Full access)
  - ECR (Full access)
  - CloudWatch Logs (Write access)
  - VPC (Read access)
  - IAM (Role creation and management)
  - Elastic Load Balancing (if using ALB)

### Installation Instructions

#### Install Docker
```bash
# Linux (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# macOS
brew install docker

# Windows
# Download Docker Desktop from https://www.docker.com/products/docker-desktop
```

#### Install AWS CLI
```bash
# Linux/macOS
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download installer from https://awscli.amazonaws.com/AWSCLIV2.msi

# Verify installation
aws --version

# Configure AWS credentials
aws configure
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd MDcomp
```

### 2. Build the Application Locally
```bash
# Using Maven
mvn clean package

# The WAR file will be generated in target/modresorts-2.0.0.war
```

### 3. Run with Docker Compose
```bash
# Build and start the application
docker-compose up --build

# Access the application
# http://localhost:8080

# Health check
# http://localhost:8080/health

# Stop the application
docker-compose down
```

### 4. Environment Variables
Create a `.env` file in the project root for local development:
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/modresorts
DATABASE_USERNAME=modresorts
DATABASE_PASSWORD=changeme
JAVA_OPTS=-Xmx512m -Xms256m
```

---

## Building and Pushing Docker Images

### Option 1: Using Build Script (Recommended)

#### Linux/macOS
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

#### Windows
```cmd
scripts\build-push.bat
```

The script will:
1. Prompt for registry selection (AWS ECR or Docker Hub)
2. Authenticate with the selected registry
3. Build the Docker image
4. Push the image to the registry

### Option 2: Manual Build and Push

#### AWS ECR
```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPO=modresorts
IMAGE_TAG=latest

# Authenticate with ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Create ECR repository (if not exists)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Build image
docker build -t $ECR_REPO:$IMAGE_TAG .

# Tag image
docker tag $ECR_REPO:$IMAGE_TAG \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG

# Push image
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG
```

#### Docker Hub
```bash
# Login to Docker Hub
docker login

# Build and tag
docker build -t username/modresorts:latest .

# Push
docker push username/modresorts:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. VPC Configuration
Ensure you have a VPC with:
- At least 2 subnets in different Availability Zones
- Internet Gateway attached (for public subnets)
- Route table configured for internet access

```bash
# List VPCs
aws ec2 describe-vpcs --region us-east-1

# List subnets
aws ec2 describe-subnets --region us-east-1
```

### 2. Security Group Configuration
Create a security group that allows:
- Inbound: Port 8080 (application port)
- Inbound: Port 80 (if using ALB)
- Outbound: All traffic

```bash
# Create security group
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts ECS tasks" \
  --vpc-id vpc-xxxxx \
  --region us-east-1

# Add inbound rule for application port
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0 \
  --region us-east-1
```

### 3. IAM Roles

#### ECS Task Execution Role
This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create trust policy file
cat > ecs-task-execution-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach AWS managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role (Optional)
This role grants permissions to the application running in the container.

```bash
# Create task role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach custom policies as needed (e.g., S3 access, DynamoDB access)
```

### 4. CloudWatch Log Group
```bash
# Create log group
aws logs create-log-group \
  --log-group-name /ecs/modresorts \
  --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/modresorts \
  --retention-in-days 7 \
  --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) defines how your container should run.

### Key Components

#### 1. Launch Type Configuration
```json
{
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc"
}
```
- **FARGATE**: Serverless compute engine for containers
- **awsvpc**: Each task gets its own elastic network interface

#### 2. CPU and Memory
```json
{
  "cpu": "512",
  "memory": "1024"
}
```

**Valid Fargate CPU/Memory Combinations:**
| CPU (vCPU) | Memory (MB) |
|------------|-------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048-8192 (increments of 1024) |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

#### 3. Container Definition
```json
{
  "containerDefinitions": [
    {
      "name": "modresorts",
      "image": "{{IMAGE_URI}}",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "JAVA_OPTS",
          "value": "-Xmx512m -Xms256m"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/modresorts",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### 4. Execution Role
```json
{
  "executionRoleArn": "arn:aws:iam::{{ACCOUNT_ID}}:role/ecsTaskExecutionRole"
}
```
Required for Fargate to pull images and write logs.

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) manages the deployment and scaling of tasks.

### Key Components

#### 1. Service Configuration
```json
{
  "serviceName": "modresorts-service",
  "desiredCount": 2,
  "launchType": "FARGATE"
}
```

#### 2. Network Configuration
```json
{
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-xxxxx", "subnet-yyyyy"],
      "securityGroups": ["sg-xxxxx"],
      "assignPublicIp": "ENABLED"
    }
  }
}
```

#### 3. Load Balancer Integration
```json
{
  "loadBalancers": [
    {
      "targetGroupArn": "arn:aws:elasticloadbalancing:...",
      "containerName": "modresorts",
      "containerPort": 8080
    }
  ],
  "healthCheckGracePeriodSeconds": 300
}
```

#### 4. Deployment Configuration
```json
{
  "deploymentConfiguration": {
    "maximumPercent": 200,
    "minimumHealthyPercent": 50,
    "deploymentCircuitBreaker": {
      "enable": true,
      "rollback": true
    }
  }
}
```

---

## Deployment to AWS ECS Fargate

### Automated Deployment (Recommended)

#### Linux/macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows
```cmd
scripts\deploy-image.bat
```

The deployment script will:
1. Prompt for AWS region and cluster name
2. Create ECS cluster (if not exists)
3. Prompt for network configuration (VPC, subnets, security group)
4. Prompt for container image URI
5. Optionally create Application Load Balancer and Target Group
6. Create CloudWatch log group
7. Register ECS task definition
8. Create or update ECS service
9. Wait for service to stabilize
10. Display deployment status and access information

### Manual Deployment

#### Step 1: Register Task Definition
```bash
# Replace placeholders in task definition
sed -i 's|{{IMAGE_URI}}|123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest|g' \
  ecs/task-definition.json
sed -i 's|{{AWS_REGION}}|us-east-1|g' ecs/task-definition.json
sed -i 's|{{ACCOUNT_ID}}|123456789012|g' ecs/task-definition.json

# Register task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1
```

#### Step 2: Create ECS Cluster
```bash
aws ecs create-cluster \
  --cluster-name modresorts-cluster \
  --region us-east-1
```

#### Step 3: Create Service
```bash
# Replace placeholders in service definition
sed -i 's|{{CLUSTER_NAME}}|modresorts-cluster|g' ecs/service-definition.json
sed -i 's|{{SUBNET_1}}|subnet-xxxxx|g' ecs/service-definition.json
sed -i 's|{{SUBNET_2}}|subnet-yyyyy|g' ecs/service-definition.json
sed -i 's|{{SECURITY_GROUP}}|sg-xxxxx|g' ecs/service-definition.json

# Create service
aws ecs create-service \
  --cli-input-json file://ecs/service-definition.json \
  --region us-east-1
```

#### Step 4: Verify Deployment
```bash
# Check service status
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster modresorts-cluster \
  --service-name modresorts-service \
  --region us-east-1
```

---

## Monitoring and Logging

### CloudWatch Logs

#### View Logs in Console
1. Navigate to CloudWatch in AWS Console
2. Select "Log groups" from the left menu
3. Find `/ecs/modresorts`
4. Click on a log stream to view logs

#### View Logs via CLI
```bash
# Tail logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# View logs for specific time range
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --start-time $(date -d '1 hour ago' +%s)000 \
  --region us-east-1
```

### CloudWatch Metrics

Key metrics to monitor:
- **CPUUtilization**: CPU usage percentage
- **MemoryUtilization**: Memory usage percentage
- **TargetResponseTime**: Application response time (if using ALB)
- **HealthyHostCount**: Number of healthy targets (if using ALB)

```bash
# Get CPU utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=modresorts-service \
               Name=ClusterName,Value=modresorts-cluster \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average \
  --region us-east-1
```

### Application Health Checks

The application exposes health check endpoints:
- **Primary**: `http://<alb-dns>/health`
- **Alternative**: `http://<alb-dns>/actuator/health`

Health check response:
```json
{
  "status": "UP",
  "application": "ModResorts",
  "version": "2.0.0"
}
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms**: Tasks transition from PENDING to STOPPED immediately

**Possible Causes**:
- Invalid CPU/memory combination
- Image pull errors
- Insufficient IAM permissions

**Solutions**:
```bash
# Check stopped task reason
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1 \
  --query 'tasks[0].stoppedReason'

# Check task logs
aws logs tail /ecs/modresorts --follow --region us-east-1

# Verify IAM role permissions
aws iam get-role --role-name ecsTaskExecutionRole
```

#### 2. Cannot Pull Image from ECR

**Symptoms**: "CannotPullContainerError" in task stopped reason

**Solutions**:
```bash
# Verify ECR repository exists
aws ecr describe-repositories --region us-east-1

# Check execution role has ECR permissions
aws iam list-attached-role-policies --role-name ecsTaskExecutionRole

# Verify image exists
aws ecr describe-images \
  --repository-name modresorts \
  --region us-east-1
```

#### 3. Service Not Reaching Steady State

**Symptoms**: Service stuck in deployment, tasks continuously restarting

**Possible Causes**:
- Application failing health checks
- Insufficient resources
- Network connectivity issues

**Solutions**:
```bash
# Check service events
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1 \
  --query 'services[0].events[0:10]'

# Test health check endpoint
curl http://<alb-dns>/health

# Check security group rules
aws ec2 describe-security-groups \
  --group-ids sg-xxxxx \
  --region us-east-1
```

#### 4. High Memory Usage

**Symptoms**: Tasks being killed due to OOM (Out of Memory)

**Solutions**:
```bash
# Increase task memory in task definition
# Update JAVA_OPTS to match new memory allocation
# Example for 2048 MB memory:
"environment": [
  {
    "name": "JAVA_OPTS",
    "value": "-Xmx1536m -Xms768m -XX:MaxRAMPercentage=75.0"
  }
]

# Re-register task definition and update service
```

#### 5. Network Connectivity Issues

**Symptoms**: Cannot access application via ALB, tasks cannot reach external services

**Solutions**:
```bash
# Verify security group allows inbound traffic on port 8080
aws ec2 describe-security-groups --group-ids sg-xxxxx

# Check subnet route tables
aws ec2 describe-route-tables --region us-east-1

# Verify NAT Gateway (if using private subnets)
aws ec2 describe-nat-gateways --region us-east-1

# Test from within VPC
aws ecs execute-command \
  --cluster modresorts-cluster \
  --task <task-id> \
  --container modresorts \
  --interactive \
  --command "/bin/bash"
```

### Debugging Commands

```bash
# Get detailed task information
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1

# View task definition
aws ecs describe-task-definition \
  --task-definition modresorts-task \
  --region us-east-1

# Check service configuration
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1

# View recent CloudWatch logs
aws logs tail /ecs/modresorts --since 10m --region us-east-1
```

---

## Scaling and Management

### Manual Scaling

#### Update Desired Count
```bash
# Scale to 4 tasks
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 4 \
  --region us-east-1
```

### Auto Scaling

#### Create Auto Scaling Target
```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1
```

#### Create Scaling Policy (CPU-based)
```bash
# Create target tracking scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }' \
  --region us-east-1
```

### Blue/Green Deployments

#### Using AWS CodeDeploy
```bash
# Create CodeDeploy application
aws deploy create-application \
  --application-name modresorts-app \
  --compute-platform ECS \
  --region us-east-1

# Create deployment group
aws deploy create-deployment-group \
  --application-name modresorts-app \
  --deployment-group-name modresorts-dg \
  --service-role-arn arn:aws:iam::123456789012:role/CodeDeployServiceRole \
  --ecs-services clusterName=modresorts-cluster,serviceName=modresorts-service \
  --load-balancer-info targetGroupInfoList=[{name=modresorts-tg}] \
  --blue-green-deployment-configuration '{
    "terminateBlueInstancesOnDeploymentSuccess": {
      "action": "TERMINATE",
      "terminationWaitTimeInMinutes": 5
    },
    "deploymentReadyOption": {
      "actionOnTimeout": "CONTINUE_DEPLOYMENT"
    }
  }' \
  --region us-east-1
```

### Rolling Updates

```bash
# Update service with new task definition
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:2 \
  --force-new-deployment \
  --region us-east-1

# Monitor deployment
aws ecs wait services-stable \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1
```

---

## Security Considerations

### 1. Container Security

#### Use Non-Root User
The Dockerfile already creates and uses a non-root user (`tomcat`):
```dockerfile
USER tomcat
```

#### Scan Images for Vulnerabilities
```bash
# Using AWS ECR image scanning
aws ecr start-image-scan \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1

# View scan results
aws ecr describe-image-scan-findings \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1
```

### 2. Network Security

#### Use Private Subnets
For production, deploy tasks in private subnets with NAT Gateway:
```json
{
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-private-1", "subnet-private-2"],
      "securityGroups": ["sg-xxxxx"],
      "assignPublicIp": "DISABLED"
    }
  }
}
```

#### Restrict Security Group Rules
```bash
# Allow only ALB to access application port
aws ec2 authorize-security-group-ingress \
  --group-id sg-app \
  --protocol tcp \
  --port 8080 \
  --source-group sg-alb \
  --region us-east-1
```

### 3. Secrets Management

#### Use AWS Secrets Manager
```bash
# Create secret
aws secretsmanager create-secret \
  --name modresorts/db-password \
  --secret-string "your-secure-password" \
  --region us-east-1

# Reference in task definition
{
  "secrets": [
    {
      "name": "DATABASE_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:modresorts/db-password"
    }
  ]
}
```

### 4. IAM Best Practices

#### Principle of Least Privilege
Grant only necessary permissions to task role:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::modresorts-bucket/*"
    }
  ]
}
```

### 5. Logging and Auditing

#### Enable CloudTrail
```bash
# Create trail for ECS API calls
aws cloudtrail create-trail \
  --name modresorts-trail \
  --s3-bucket-name modresorts-cloudtrail \
  --region us-east-1

# Start logging
aws cloudtrail start-logging \
  --name modresorts-trail \
  --region us-east-1
```

---

## Technology-Specific Notes

### Java EE 7 and Tomcat 9

#### JVM Tuning for Containers
The Dockerfile includes optimized JVM settings:
```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

**Explanation**:
- `-Xmx512m`: Maximum heap size (adjust based on container memory)
- `-Xms256m`: Initial heap size
- `-XX:+UseContainerSupport`: Enable container awareness
- `-XX:MaxRAMPercentage=75.0`: Use up to 75% of container memory for heap

#### Tomcat Configuration
Default Tomcat configuration is suitable for most use cases. For custom configuration:
1. Create custom `server.xml`
2. Mount as volume in docker-compose.yml or copy in Dockerfile

#### Session Management
For multi-instance deployments, consider:
- Sticky sessions (ALB session affinity)
- External session store (Redis, DynamoDB)

### Maven Build Optimization

The Dockerfile uses multi-stage build for efficiency:
```dockerfile
# Stage 1: Build
FROM maven:3.9.4-eclipse-temurin-8 AS builder
WORKDIR /workspace
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM amazoncorretto:8
# ... copy WAR from builder stage
```

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Definition](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html)

### Docker Documentation
- [Dockerfile Best Practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

### Java and Tomcat
- [Tomcat 9 Documentation](https://tomcat.apache.org/tomcat-9.0-doc/)
- [Java EE 7 Specification](https://javaee.github.io/javaee-spec/javadocs/)

---

## Support and Maintenance

### Regular Maintenance Tasks
1. **Update base images**: Regularly update to latest security patches
2. **Review logs**: Monitor CloudWatch logs for errors and warnings
3. **Check metrics**: Review CloudWatch metrics for performance issues
4. **Update dependencies**: Keep Maven dependencies up to date
5. **Rotate secrets**: Regularly rotate database passwords and API keys

### Backup and Disaster Recovery
1. **ECR image backups**: Enable ECR lifecycle policies
2. **Configuration backups**: Store task/service definitions in version control
3. **Database backups**: Implement regular RDS snapshots (if using RDS)
4. **Multi-region deployment**: Consider deploying to multiple regions for HA

---

## Conclusion

This guide provides comprehensive instructions for deploying the ModResorts application to AWS ECS Fargate. Follow the steps carefully, and refer to the troubleshooting section if you encounter any issues.

For questions or support, please contact the development team or refer to the AWS documentation.

**Happy Deploying! 🚀**
