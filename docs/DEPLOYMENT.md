# ModResorts - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Fargate Setup](#ecs-fargate-setup)
7. [Deployment Walkthrough](#deployment-walkthrough)
8. [Configuration Management](#configuration-management)
9. [Monitoring and Logging](#monitoring-and-logging)
10. [Troubleshooting](#troubleshooting)
11. [Scaling and Management](#scaling-and-management)
12. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Java EE 7 web application packaged as a WAR file, designed to run on Apache Tomcat in containerized environments. This guide covers deployment to AWS ECS Fargate, a serverless container orchestration platform.

**Application Details:**
- **Technology Stack**: Java 8, Java EE 7, Maven
- **Application Server**: Apache Tomcat 9.0
- **Package Type**: WAR (Web Application Archive)
- **Default Port**: 8080
- **Health Check Endpoint**: `/health` and `/actuator/health`

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
- **AWS CLI**: Version 2.x
- **Maven**: Version 3.6 or higher (for local builds)
- **Git**: For version control

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (Elastic Container Service)
  - ECR (Elastic Container Registry)
  - EC2 (for VPC, subnets, security groups)
  - IAM (for role creation)
  - CloudWatch Logs
  - Elastic Load Balancing (optional)

### Installation Instructions

#### Docker
```bash
# Linux (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# macOS
brew install docker

# Windows
# Download Docker Desktop from https://www.docker.com/products/docker-desktop
```

#### AWS CLI
```bash
# Linux/macOS
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download installer from https://awscli.amazonaws.com/AWSCLIV2.msi
```

#### Configure AWS CLI
```bash
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region (e.g., us-east-1)
# Enter default output format (json)
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd RojaTest2
```

### 2. Build the Application Locally
```bash
# Using Maven
mvn clean package

# The WAR file will be created at: target/modresorts-2.0.0.war
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

### 4. Test the Application
```bash
# Health check
curl http://localhost:8080/health

# Expected response:
# {"status":"UP","application":"ModResorts","version":"2.0.0"}
```

---

## Building and Pushing Docker Images

### Using build-push.sh (Linux/macOS)

```bash
cd scripts
chmod +x build-push.sh
./build-push.sh
```

**Script Workflow:**
1. Prompts for image tag (default: latest)
2. Asks for registry selection (AWS ECR or Docker Hub)
3. Collects registry credentials
4. Builds Docker image
5. Authenticates with registry
6. Pushes image to registry

### Using build-push.bat (Windows)

```cmd
cd scripts
build-push.bat
```

### Manual Docker Build

```bash
# Build the image
docker build -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. Create VPC and Networking Components

#### Option A: Use Default VPC
```bash
# List available VPCs
aws ec2 describe-vpcs --query 'Vpcs[*].[VpcId,IsDefault,CidrBlock]' --output table

# List subnets in default VPC
aws ec2 describe-subnets --filters "Name=vpc-id,Values=<vpc-id>" --query 'Subnets[*].[SubnetId,AvailabilityZone,CidrBlock]' --output table
```

#### Option B: Create New VPC (Recommended for Production)
```bash
# Create VPC
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=modresorts-vpc}]'

# Create subnets in different availability zones
aws ec2 create-subnet --vpc-id <vpc-id> --cidr-block 10.0.1.0/24 --availability-zone us-east-1a
aws ec2 create-subnet --vpc-id <vpc-id> --cidr-block 10.0.2.0/24 --availability-zone us-east-1b

# Create and attach Internet Gateway
aws ec2 create-internet-gateway --tag-specifications 'ResourceType=internet-gateway,Tags=[{Key=Name,Value=modresorts-igw}]'
aws ec2 attach-internet-gateway --vpc-id <vpc-id> --internet-gateway-id <igw-id>

# Create route table and add route to Internet Gateway
aws ec2 create-route-table --vpc-id <vpc-id>
aws ec2 create-route --route-table-id <rtb-id> --destination-cidr-block 0.0.0.0/0 --gateway-id <igw-id>

# Associate route table with subnets
aws ec2 associate-route-table --subnet-id <subnet-1-id> --route-table-id <rtb-id>
aws ec2 associate-route-table --subnet-id <subnet-2-id> --route-table-id <rtb-id>
```

### 2. Create Security Group

```bash
# Create security group
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts ECS tasks" \
  --vpc-id <vpc-id>

# Allow inbound HTTP traffic on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow inbound HTTP traffic on port 80 (for ALB)
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0

# Allow all outbound traffic (default)
```

### 3. Create IAM Roles

#### ECS Task Execution Role
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

#### ECS Task Role (Optional - for application permissions)
```bash
# Create task role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach policies as needed (e.g., S3, DynamoDB access)
```

### 4. Create ECR Repository

```bash
# Create repository
aws ecr create-repository --repository-name modresorts --region us-east-1

# Get repository URI
aws ecr describe-repositories --repository-names modresorts --query 'repositories[0].repositoryUri' --output text
```

---

## ECS Fargate Setup

### Understanding ECS Components

#### Task Definition
- Defines container specifications (image, CPU, memory, ports)
- Specifies IAM roles for task execution and application permissions
- Configures logging to CloudWatch
- **Network Mode**: Must be `awsvpc` for Fargate
- **Launch Type**: Must be `FARGATE`

#### Service
- Maintains desired number of task instances
- Integrates with load balancers
- Handles rolling deployments
- Provides service discovery

#### Cluster
- Logical grouping of tasks and services
- Can contain multiple services

### Valid Fargate CPU/Memory Combinations

| CPU (vCPU) | Memory (MB) Options |
|------------|---------------------|
| 256 (.25)  | 512, 1024, 2048 |
| 512 (.5)   | 1024, 2048, 3072, 4096 |
| 1024 (1)   | 2048, 3072, 4096, 5120, 6144, 7168, 8192 |
| 2048 (2)   | 4096-16384 (increments of 1024) |
| 4096 (4)   | 8192-30720 (increments of 1024) |

**Default Configuration**: CPU: 512, Memory: 1024

### CloudWatch Log Group Setup

```bash
# Create log group
aws logs create-log-group --log-group-name /ecs/modresorts --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy --log-group-name /ecs/modresorts --retention-in-days 7
```

---

## Deployment Walkthrough

### Step 1: Build and Push Docker Image

```bash
# Navigate to scripts directory
cd scripts

# Run build-push script
./build-push.sh

# Follow prompts:
# 1. Enter image tag (e.g., v1.0.0 or latest)
# 2. Select registry (1 for ECR, 2 for Docker Hub)
# 3. Enter AWS region (e.g., us-east-1)
# 4. Enter AWS Account ID
# 5. Enter ECR repository name (default: modresorts)
```

### Step 2: Deploy to ECS Fargate

```bash
# Run deployment script
./deploy-image.sh

# Follow prompts:
# 1. Enter AWS Region
# 2. Enter ECS Cluster Name (will be created if doesn't exist)
# 3. Enter VPC ID
# 4. Enter Subnet IDs (comma-separated, at least 2)
# 5. Enter Security Group ID
# 6. Enter ECR Image URI
# 7. Choose whether to create load balancer (y/n)
```

### Step 3: Verify Deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster <cluster-name> \
  --services modresorts-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster <cluster-name> \
  --service-name modresorts-service \
  --region us-east-1

# View task details
aws ecs describe-tasks \
  --cluster <cluster-name> \
  --tasks <task-arn> \
  --region us-east-1
```

### Step 4: Access the Application

#### With Load Balancer
```bash
# Get ALB DNS name
aws elbv2 describe-load-balancers \
  --names modresorts-alb \
  --query 'LoadBalancers[0].DNSName' \
  --output text

# Access application
curl http://<alb-dns-name>/health
```

#### Without Load Balancer (Direct Task Access)
```bash
# Get task public IP
aws ecs describe-tasks \
  --cluster <cluster-name> \
  --tasks <task-arn> \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text

# Get public IP from network interface
aws ec2 describe-network-interfaces \
  --network-interface-ids <eni-id> \
  --query 'NetworkInterfaces[0].Association.PublicIp' \
  --output text

# Access application
curl http://<public-ip>:8080/health
```

---

## Configuration Management

### Environment Variables

Environment variables are defined in the task definition (`ecs/task-definition.json`):

```json
"environment": [
  {
    "name": "JAVA_OPTS",
    "value": "-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
  },
  {
    "name": "CATALINA_OPTS",
    "value": "-Duser.timezone=UTC"
  },
  {
    "name": "TZ",
    "value": "UTC"
  }
]
```

### Secrets Management

For sensitive data, use AWS Secrets Manager or Systems Manager Parameter Store:

```json
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:region:account-id:secret:secret-name"
  }
]
```

### JVM Tuning

Adjust JVM settings based on container memory:

| Container Memory | Recommended Xmx | Recommended Xms |
|------------------|-----------------|-----------------|
| 512 MB           | 384m            | 192m            |
| 1024 MB          | 768m            | 384m            |
| 2048 MB          | 1536m           | 768m            |

---

## Monitoring and Logging

### CloudWatch Logs

```bash
# View logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# View logs for specific time range
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --start-time $(date -d '1 hour ago' +%s)000 \
  --region us-east-1

# Search logs
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### CloudWatch Metrics

Key metrics to monitor:
- **CPUUtilization**: Target < 70%
- **MemoryUtilization**: Target < 80%
- **TargetResponseTime**: Target < 500ms
- **HealthyHostCount**: Should match desired count
- **UnHealthyHostCount**: Should be 0

### Setting Up Alarms

```bash
# CPU utilization alarm
aws cloudwatch put-metric-alarm \
  --alarm-name modresorts-high-cpu \
  --alarm-description "Alert when CPU exceeds 70%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 70 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=<cluster-name>
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms**: Tasks transition from PENDING to STOPPED immediately

**Possible Causes**:
- Invalid CPU/memory combination
- Image pull errors
- IAM role permissions issues

**Solutions**:
```bash
# Check stopped task reason
aws ecs describe-tasks \
  --cluster <cluster-name> \
  --tasks <task-arn> \
  --query 'tasks[0].stoppedReason'

# Verify task execution role has ECR permissions
aws iam get-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name AmazonECSTaskExecutionRolePolicy

# Check CloudWatch logs for errors
aws logs tail /ecs/modresorts --since 1h
```

#### 2. Health Check Failures

**Symptoms**: Tasks marked as unhealthy, continuous restarts

**Solutions**:
```bash
# Verify health endpoint is accessible
curl http://<task-ip>:8080/health

# Check health check configuration in target group
aws elbv2 describe-target-health \
  --target-group-arn <tg-arn>

# Increase health check grace period
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --health-check-grace-period-seconds 300
```

#### 3. Network Connectivity Issues

**Symptoms**: Cannot access application, timeout errors

**Solutions**:
```bash
# Verify security group rules
aws ec2 describe-security-groups --group-ids <sg-id>

# Check subnet route tables
aws ec2 describe-route-tables --filters "Name=association.subnet-id,Values=<subnet-id>"

# Verify Internet Gateway attachment
aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=<vpc-id>"

# Ensure assignPublicIp is ENABLED for public access
```

#### 4. Out of Memory Errors

**Symptoms**: Tasks crash with OOM errors in logs

**Solutions**:
```bash
# Increase task memory in task definition
# Update JAVA_OPTS to use less heap
# Example: -Xmx384m for 512MB container

# Update task definition
aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json

# Update service with new task definition
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --task-definition modresorts-task:2
```

#### 5. Deployment Failures

**Symptoms**: Service fails to reach steady state

**Solutions**:
```bash
# Check service events
aws ecs describe-services \
  --cluster <cluster-name> \
  --services modresorts-service \
  --query 'services[0].events[0:10]'

# Rollback to previous task definition
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --task-definition modresorts-task:1

# Enable circuit breaker for automatic rollback
# (Already configured in service-definition.json)
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale service to 5 tasks
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --desired-count 5

# Scale down to 1 task
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --desired-count 1
```

### Auto Scaling

#### Target Tracking Scaling (Recommended)

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<cluster-name>/modresorts-service \
  --min-capacity 2 \
  --max-capacity 10

# Create scaling policy based on CPU
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/<cluster-name>/modresorts-service \
  --policy-name cpu-target-tracking \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://cpu-scaling-policy.json
```

**cpu-scaling-policy.json**:
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

### Blue/Green Deployments

```bash
# Create new task definition revision
aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json

# Update service with new task definition
aws ecs update-service \
  --cluster <cluster-name> \
  --service modresorts-service \
  --task-definition modresorts-task:2 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100"

# Monitor deployment
aws ecs describe-services \
  --cluster <cluster-name> \
  --services modresorts-service \
  --query 'services[0].deployments'
```

### Rolling Updates

The service is configured with:
- **maximumPercent**: 200 (allows double capacity during deployment)
- **minimumHealthyPercent**: 50 (maintains at least 50% capacity)

This enables zero-downtime deployments.

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as non-root user (uid 1001)
- **Read-only Root Filesystem**: Consider enabling for enhanced security
- **Security Scanning**: Scan images for vulnerabilities

```bash
# Scan image with AWS ECR
aws ecr start-image-scan \
  --repository-name modresorts \
  --image-id imageTag=latest

# Get scan results
aws ecr describe-image-scan-findings \
  --repository-name modresorts \
  --image-id imageTag=latest
```

### 2. Network Security

- **Security Groups**: Restrict inbound traffic to necessary ports only
- **Private Subnets**: Use private subnets with NAT Gateway for production
- **VPC Endpoints**: Use VPC endpoints for AWS services (ECR, CloudWatch)

### 3. IAM Security

- **Least Privilege**: Grant minimum required permissions
- **Task Role**: Use task role for application-level AWS API access
- **Execution Role**: Use execution role only for ECS infrastructure

### 4. Secrets Management

```bash
# Store database password in Secrets Manager
aws secretsmanager create-secret \
  --name modresorts/db-password \
  --secret-string "your-secure-password"

# Reference in task definition
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:modresorts/db-password"
  }
]
```

### 5. Logging and Auditing

- **CloudTrail**: Enable for API call auditing
- **VPC Flow Logs**: Monitor network traffic
- **Container Insights**: Enable for enhanced monitoring

```bash
# Enable Container Insights
aws ecs update-cluster-settings \
  --cluster <cluster-name> \
  --settings name=containerInsights,value=enabled
```

---

## Technology-Specific Notes

### Java EE 7 on Tomcat

#### Session Management
- Default session timeout: 30 minutes (configured in web.xml)
- For sticky sessions with ALB, enable session affinity:

```bash
aws elbv2 modify-target-group-attributes \
  --target-group-arn <tg-arn> \
  --attributes Key=stickiness.enabled,Value=true Key=stickiness.type,Value=lb_cookie
```

#### JNDI Resources
- Configure JNDI resources in Tomcat context.xml
- Mount as volume or include in Docker image

#### Form-Based Authentication
- Application uses form-based authentication (login.jsp)
- Security constraints are commented out for demo purposes
- Enable security constraints in production

### Performance Tuning

#### JVM Settings
```bash
# For 1GB container
JAVA_OPTS="-Xmx768m -Xms384m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# For 2GB container
JAVA_OPTS="-Xmx1536m -Xms768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

#### Tomcat Tuning
```xml
<!-- server.xml -->
<Connector port="8080" protocol="HTTP/1.1"
           maxThreads="200"
           minSpareThreads="25"
           maxConnections="10000"
           connectionTimeout="20000"
           redirectPort="8443" />
```

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)

### Best Practices
- [ECS Best Practices Guide](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Java in Containers](https://developers.redhat.com/blog/2017/03/14/java-inside-docker)

### Support
- AWS Support: https://console.aws.amazon.com/support/
- ECS Forum: https://forums.aws.amazon.com/forum.jspa?forumID=187

---

## Appendix

### Quick Reference Commands

```bash
# Build and push
./scripts/build-push.sh

# Deploy to ECS
./scripts/deploy-image.sh

# View logs
aws logs tail /ecs/modresorts --follow

# Scale service
aws ecs update-service --cluster <cluster> --service modresorts-service --desired-count 3

# Update service with new image
aws ecs update-service --cluster <cluster> --service modresorts-service --force-new-deployment

# Stop all tasks
aws ecs update-service --cluster <cluster> --service modresorts-service --desired-count 0

# Delete service
aws ecs delete-service --cluster <cluster> --service modresorts-service --force

# Delete cluster
aws ecs delete-cluster --cluster <cluster>
```

### Useful AWS CLI Queries

```bash
# List all ECS clusters
aws ecs list-clusters

# List services in cluster
aws ecs list-services --cluster <cluster>

# Get task IPs
aws ecs describe-tasks --cluster <cluster> --tasks <task-id> --query 'tasks[0].attachments[0].details[?name==`privateIPv4Address`].value' --output text

# Get service metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=<cluster> \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average
```

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Maintained By**: DevOps Team
