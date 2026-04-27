# ModResorts Application - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Testing Locally](#building-and-testing-locally)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [Building and Pushing Docker Image](#building-and-pushing-docker-image)
7. [ECS Fargate Deployment](#ecs-fargate-deployment)
8. [Monitoring and Logging](#monitoring-and-logging)
9. [Troubleshooting](#troubleshooting)
10. [Scaling and Management](#scaling-and-management)
11. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Java EE 7 web application packaged as a WAR file, designed to run in a containerized Tomcat environment. This guide covers deploying the application to AWS ECS Fargate, a serverless container orchestration platform.

**Application Details:**
- **Technology Stack**: Java 8, Java EE 7, Servlet 3.1
- **Build Tool**: Maven 3.9.4
- **Package Type**: WAR (Web Application Archive)
- **Runtime**: Apache Tomcat 9.0
- **Application Port**: 8080
- **Health Endpoint**: `/health` and `/actuator/health`
- **Version**: 2.0.0

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 1.29 or higher (for local testing)
- **AWS CLI**: Version 2.x
- **Git**: For version control
- **Java 8 JDK**: For local development (optional)
- **Maven 3.6+**: For local builds (optional)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (Elastic Container Service)
  - ECR (Elastic Container Registry)
  - VPC and networking
  - CloudWatch Logs
  - IAM role creation
  - Application Load Balancer (optional)

### Install AWS CLI

**Linux/macOS:**
```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

**Windows:**
Download and run the AWS CLI MSI installer from: https://aws.amazon.com/cli/

**Configure AWS CLI:**
```bash
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter your default region (e.g., us-east-1)
# Enter your default output format (json)
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd MDRComp
```

### 2. Build Locally with Maven (Optional)
```bash
mvn clean package
```

The WAR file will be generated in `target/modresorts-2.0.0.war`

### 3. Run with Docker Compose
```bash
docker-compose up --build
```

Access the application at: http://localhost:8080

**Health Check:**
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{"status":"UP","application":"ModResorts","version":"2.0.0"}
```

### 4. Stop the Application
```bash
docker-compose down
```

---

## Building and Testing Locally

### Build Docker Image Manually
```bash
docker build -t modresorts:latest .
```

### Run Container Manually
```bash
docker run -d \
  --name modresorts-app \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xmx512m -Xms256m" \
  modresorts:latest
```

### View Logs
```bash
docker logs -f modresorts-app
```

### Test the Application
```bash
# Health check
curl http://localhost:8080/health

# Welcome endpoint
curl http://localhost:8080/welcome

# Weather endpoint
curl http://localhost:8080/weather
```

### Stop and Remove Container
```bash
docker stop modresorts-app
docker rm modresorts-app
```

---

## AWS ECS Fargate Prerequisites

### 1. Create VPC and Networking (if not exists)

**Create VPC:**
```bash
aws ec2 create-vpc \
  --cidr-block 10.0.0.0/16 \
  --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=modresorts-vpc}]'
```

**Create Subnets (at least 2 in different AZs):**
```bash
# Subnet 1
aws ec2 create-subnet \
  --vpc-id <vpc-id> \
  --cidr-block 10.0.1.0/24 \
  --availability-zone us-east-1a \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=modresorts-subnet-1}]'

# Subnet 2
aws ec2 create-subnet \
  --vpc-id <vpc-id> \
  --cidr-block 10.0.2.0/24 \
  --availability-zone us-east-1b \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=modresorts-subnet-2}]'
```

**Create Internet Gateway:**
```bash
aws ec2 create-internet-gateway \
  --tag-specifications 'ResourceType=internet-gateway,Tags=[{Key=Name,Value=modresorts-igw}]'

aws ec2 attach-internet-gateway \
  --vpc-id <vpc-id> \
  --internet-gateway-id <igw-id>
```

**Create Route Table:**
```bash
aws ec2 create-route-table \
  --vpc-id <vpc-id> \
  --tag-specifications 'ResourceType=route-table,Tags=[{Key=Name,Value=modresorts-rt}]'

aws ec2 create-route \
  --route-table-id <rt-id> \
  --destination-cidr-block 0.0.0.0/0 \
  --gateway-id <igw-id>

# Associate subnets with route table
aws ec2 associate-route-table --subnet-id <subnet-1-id> --route-table-id <rt-id>
aws ec2 associate-route-table --subnet-id <subnet-2-id> --route-table-id <rt-id>
```

### 2. Create Security Group

```bash
aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts application" \
  --vpc-id <vpc-id>

# Allow inbound traffic on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow inbound traffic on port 80 (for ALB)
aws ec2 authorize-security-group-ingress \
  --group-id <sg-id> \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0
```

### 3. Create IAM Roles

**ECS Task Execution Role:**
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

**ECS Task Role (for application permissions):**
```bash
# Create task role
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach policies as needed (e.g., S3, DynamoDB, etc.)
```

### 4. Create CloudWatch Log Group

```bash
aws logs create-log-group --log-group-name /ecs/modresorts
```

---

## Building and Pushing Docker Image

### Option 1: Using the Build Script (Recommended)

**Linux/macOS:**
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

**Windows:**
```cmd
scripts\build-push.bat
```

The script will:
1. Prompt for registry selection (AWS ECR or Docker Hub)
2. Authenticate with the selected registry
3. Build the Docker image
4. Tag the image appropriately
5. Push the image to the registry

### Option 2: Manual Build and Push

**For AWS ECR:**

1. **Create ECR Repository:**
```bash
aws ecr create-repository --repository-name modresorts --region us-east-1
```

2. **Authenticate Docker to ECR:**
```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
```

3. **Build and Tag Image:**
```bash
docker build -t modresorts:latest .
docker tag modresorts:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

4. **Push Image:**
```bash
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

**For Docker Hub:**

1. **Login to Docker Hub:**
```bash
docker login
```

2. **Build and Tag Image:**
```bash
docker build -t <username>/modresorts:latest .
```

3. **Push Image:**
```bash
docker push <username>/modresorts:latest
```

---

## ECS Fargate Deployment

### Understanding ECS Fargate

AWS ECS Fargate is a serverless compute engine for containers that:
- Eliminates the need to manage EC2 instances
- Automatically scales based on demand
- Charges only for resources used
- Provides built-in security and isolation

### ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) defines:

**Launch Type Configuration:**
- `requiresCompatibilities`: ["FARGATE"] - Specifies Fargate launch type
- `networkMode`: "awsvpc" - Required for Fargate (each task gets its own ENI)

**CPU and Memory:**
- `cpu`: "512" (0.5 vCPU)
- `memory`: "1024" (1 GB)

**Valid Fargate CPU/Memory Combinations:**
- CPU: 256 (.25 vCPU) → Memory: 512, 1024, 2048 MB
- CPU: 512 (.5 vCPU) → Memory: 1024, 2048, 3072, 4096 MB
- CPU: 1024 (1 vCPU) → Memory: 2048-8192 MB (increments of 1024)
- CPU: 2048 (2 vCPU) → Memory: 4096-16384 MB
- CPU: 4096 (4 vCPU) → Memory: 8192-30720 MB

**Container Definition:**
- `name`: Container name (modresorts)
- `image`: ECR image URI
- `portMappings`: Container port 8080
- `environment`: Environment variables for JVM tuning
- `healthCheck`: Health check configuration using curl
- `logConfiguration`: CloudWatch Logs integration

**IAM Roles:**
- `executionRoleArn`: Allows ECS to pull images and write logs
- `taskRoleArn`: Grants permissions to the application (optional)

### ECS Service Configuration

The service definition (`ecs/service-definition.json`) defines:

**Service Settings:**
- `serviceName`: Name of the ECS service
- `desiredCount`: Number of tasks to run (2 for high availability)
- `launchType`: "FARGATE"

**Network Configuration:**
- `awsvpcConfiguration`: VPC, subnets, security groups
- `assignPublicIp`: "ENABLED" (for internet access)

**Deployment Configuration:**
- `maximumPercent`: 200 (allows rolling updates)
- `minimumHealthyPercent`: 50 (ensures availability during updates)
- `deploymentCircuitBreaker`: Automatic rollback on failure

**Load Balancer (Optional):**
- `targetGroupArn`: ALB target group ARN
- `containerName`: Container to route traffic to
- `containerPort`: Port to route traffic to (8080)
- `healthCheckGracePeriodSeconds`: 300 (5 minutes for startup)

### Deployment Steps

**Option 1: Using the Deployment Script (Recommended)**

**Linux/macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will:
1. Prompt for AWS region and ECS cluster name
2. Create ECS cluster if it doesn't exist
3. Prompt for network configuration (VPC, subnets, security group)
4. Prompt for ECR image URI
5. Optionally create Application Load Balancer and Target Group
6. Register task definition
7. Create or update ECS service
8. Wait for service to become stable
9. Display deployment details and access information

**Option 2: Manual Deployment**

1. **Register Task Definition:**
```bash
# Update placeholders in task-definition.json
sed -i 's|{{IMAGE_URI}}|<your-image-uri>|g' ecs/task-definition.json
sed -i 's|{{AWS_REGION}}|us-east-1|g' ecs/task-definition.json
sed -i 's|{{ACCOUNT_ID}}|<your-account-id>|g' ecs/task-definition.json

# Register task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1
```

2. **Create ECS Cluster:**
```bash
aws ecs create-cluster --cluster-name modresorts-cluster --region us-east-1
```

3. **Create Target Group (if using ALB):**
```bash
aws elbv2 create-target-group \
  --name modresorts-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id <vpc-id> \
  --target-type ip \
  --health-check-path /health \
  --region us-east-1
```

4. **Create Application Load Balancer (if needed):**
```bash
aws elbv2 create-load-balancer \
  --name modresorts-alb \
  --subnets <subnet-1> <subnet-2> \
  --security-groups <sg-id> \
  --region us-east-1
```

5. **Create Listener:**
```bash
aws elbv2 create-listener \
  --load-balancer-arn <alb-arn> \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=<tg-arn> \
  --region us-east-1
```

6. **Update Service Definition:**
```bash
# Update placeholders in service-definition.json
sed -i 's|{{CLUSTER_NAME}}|modresorts-cluster|g' ecs/service-definition.json
sed -i 's|{{SUBNET_1}}|<subnet-1>|g' ecs/service-definition.json
sed -i 's|{{SUBNET_2}}|<subnet-2>|g' ecs/service-definition.json
sed -i 's|{{SECURITY_GROUP}}|<sg-id>|g' ecs/service-definition.json
sed -i 's|{{TARGET_GROUP_ARN}}|<tg-arn>|g' ecs/service-definition.json
```

7. **Create ECS Service:**
```bash
aws ecs create-service \
  --cli-input-json file://ecs/service-definition.json \
  --region us-east-1
```

8. **Wait for Service Stability:**
```bash
aws ecs wait services-stable \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1
```

---

## Monitoring and Logging

### CloudWatch Logs

**View Logs:**
```bash
# Tail logs in real-time
aws logs tail /ecs/modresorts --follow --region us-east-1

# View logs for specific time range
aws logs tail /ecs/modresorts \
  --since 1h \
  --region us-east-1

# Filter logs
aws logs tail /ecs/modresorts \
  --filter-pattern "ERROR" \
  --follow \
  --region us-east-1
```

**CloudWatch Console:**
1. Navigate to CloudWatch → Log groups
2. Select `/ecs/modresorts`
3. View log streams for each task

### ECS Service Monitoring

**Describe Service:**
```bash
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1
```

**List Running Tasks:**
```bash
aws ecs list-tasks \
  --cluster modresorts-cluster \
  --service-name modresorts-service \
  --region us-east-1
```

**Describe Task:**
```bash
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1
```

### CloudWatch Metrics

Key metrics to monitor:
- **CPUUtilization**: CPU usage percentage
- **MemoryUtilization**: Memory usage percentage
- **TargetResponseTime**: Response time from targets
- **HealthyHostCount**: Number of healthy targets
- **UnHealthyHostCount**: Number of unhealthy targets

**Create CloudWatch Dashboard:**
```bash
aws cloudwatch put-dashboard \
  --dashboard-name modresorts-dashboard \
  --dashboard-body file://cloudwatch-dashboard.json
```

### Application Load Balancer Monitoring

**View Target Health:**
```bash
aws elbv2 describe-target-health \
  --target-group-arn <tg-arn> \
  --region us-east-1
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms:**
- Tasks transition from PENDING to STOPPED
- No running tasks in the service

**Possible Causes and Solutions:**

**a) Image Pull Errors:**
```bash
# Check task stopped reason
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --query 'tasks[0].stoppedReason'

# Verify ECR permissions
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Verify image exists
aws ecr describe-images \
  --repository-name modresorts \
  --region us-east-1
```

**b) Invalid CPU/Memory Configuration:**
- Ensure CPU and memory values are valid Fargate combinations
- Update task definition with valid values

**c) Network Configuration Issues:**
- Verify subnets have internet access (via Internet Gateway or NAT Gateway)
- Check security group allows outbound traffic
- Ensure subnets are in different availability zones

#### 2. Health Check Failures

**Symptoms:**
- Tasks start but are marked as unhealthy
- Load balancer shows unhealthy targets

**Solutions:**

**a) Increase Health Check Grace Period:**
```bash
# Update service with longer grace period
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --health-check-grace-period-seconds 300
```

**b) Verify Health Endpoint:**
```bash
# Get task public IP
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text

# Test health endpoint
curl http://<task-ip>:8080/health
```

**c) Check Application Logs:**
```bash
aws logs tail /ecs/modresorts --follow --region us-east-1
```

#### 3. Service Not Accessible via Load Balancer

**Symptoms:**
- Service is running but ALB returns 503 or timeout

**Solutions:**

**a) Verify Target Group Health:**
```bash
aws elbv2 describe-target-health \
  --target-group-arn <tg-arn>
```

**b) Check Security Group Rules:**
- Ensure security group allows inbound traffic on port 8080
- Verify ALB security group allows inbound traffic on port 80

**c) Verify Target Group Configuration:**
- Target type must be "ip" for Fargate
- Health check path should be "/health"
- Port should be 8080

#### 4. Out of Memory Errors

**Symptoms:**
- Tasks restart frequently
- Logs show OutOfMemoryError

**Solutions:**

**a) Increase Task Memory:**
```json
{
  "cpu": "1024",
  "memory": "2048"
}
```

**b) Tune JVM Heap Size:**
```json
{
  "name": "JAVA_OPTS",
  "value": "-Xmx1536m -Xms768m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
}
```

#### 5. Deployment Failures

**Symptoms:**
- New task definition doesn't deploy
- Service remains on old version

**Solutions:**

**a) Check Deployment Circuit Breaker:**
```bash
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --query 'services[0].deployments'
```

**b) Force New Deployment:**
```bash
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force-new-deployment
```

**c) Rollback to Previous Version:**
```bash
# List task definition revisions
aws ecs list-task-definitions \
  --family-prefix modresorts-task

# Update service to previous revision
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:1
```

### Debugging Commands

**View Task Logs:**
```bash
# Get task ID
TASK_ID=$(aws ecs list-tasks \
  --cluster modresorts-cluster \
  --service-name modresorts-service \
  --query 'taskArns[0]' \
  --output text | cut -d'/' -f3)

# View logs
aws logs tail /ecs/modresorts --follow --region us-east-1
```

**Execute Command in Running Task:**
```bash
aws ecs execute-command \
  --cluster modresorts-cluster \
  --task <task-id> \
  --container modresorts \
  --interactive \
  --command "/bin/bash"
```

**Check Task Network Configuration:**
```bash
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --query 'tasks[0].attachments[0].details'
```

---

## Scaling and Management

### Manual Scaling

**Update Desired Count:**
```bash
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 4
```

### Auto Scaling

**Create Auto Scaling Target:**
```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10
```

**Create Scaling Policy (CPU-based):**
```bash
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
  }'
```

**Create Scaling Policy (Memory-based):**
```bash
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name memory-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 80.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageMemoryUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployments

**Using AWS CodeDeploy:**

1. **Create CodeDeploy Application:**
```bash
aws deploy create-application \
  --application-name modresorts-app \
  --compute-platform ECS
```

2. **Create Deployment Group:**
```bash
aws deploy create-deployment-group \
  --application-name modresorts-app \
  --deployment-group-name modresorts-dg \
  --service-role-arn <codedeploy-role-arn> \
  --ecs-services clusterName=modresorts-cluster,serviceName=modresorts-service \
  --load-balancer-info targetGroupPairInfoList=[{targetGroups=[{name=modresorts-tg-blue},{name=modresorts-tg-green}],prodTrafficRoute={listenerArns=[<listener-arn>]}}] \
  --deployment-style deploymentType=BLUE_GREEN,deploymentOption=WITH_TRAFFIC_CONTROL \
  --blue-green-deployment-configuration '{
    "terminateBlueInstancesOnDeploymentSuccess": {
      "action": "TERMINATE",
      "terminationWaitTimeInMinutes": 5
    },
    "deploymentReadyOption": {
      "actionOnTimeout": "CONTINUE_DEPLOYMENT"
    }
  }'
```

### Rolling Updates

**Update Task Definition:**
```bash
# Register new task definition
aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json

# Update service
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --task-definition modresorts-task:2
```

**Monitor Deployment:**
```bash
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --query 'services[0].deployments'
```

### Service Updates

**Update Environment Variables:**
```bash
# Update task definition with new environment variables
# Register new task definition
# Update service to use new task definition
```

**Update Resource Limits:**
```bash
# Modify task definition CPU/memory
# Register new task definition
# Update service
```

---

## Security Considerations

### Container Security

**1. Use Non-Root User:**
The Dockerfile creates and uses a non-root user (`tomcat`) for running the application.

**2. Minimal Base Image:**
Uses official Tomcat image based on Eclipse Temurin JRE (minimal attack surface).

**3. No Secrets in Environment Variables:**
Use AWS Secrets Manager or Parameter Store for sensitive data:

```bash
# Store secret
aws secretsmanager create-secret \
  --name modresorts/db-password \
  --secret-string "your-password"

# Reference in task definition
{
  "secrets": [
    {
      "name": "DB_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:region:account-id:secret:modresorts/db-password"
    }
  ]
}
```

### Network Security

**1. Security Group Configuration:**
- Restrict inbound traffic to necessary ports only
- Use security group rules instead of CIDR blocks when possible
- Implement least privilege access

**2. Private Subnets (Recommended for Production):**
- Deploy tasks in private subnets
- Use NAT Gateway for outbound internet access
- Place ALB in public subnets

**3. VPC Flow Logs:**
```bash
aws ec2 create-flow-logs \
  --resource-type VPC \
  --resource-ids <vpc-id> \
  --traffic-type ALL \
  --log-destination-type cloud-watch-logs \
  --log-group-name /aws/vpc/modresorts
```

### IAM Security

**1. Task Execution Role:**
- Minimal permissions for pulling images and writing logs
- Use AWS managed policy: `AmazonECSTaskExecutionRolePolicy`

**2. Task Role:**
- Grant only necessary permissions for application
- Use separate roles for different environments
- Implement resource-based policies

**3. Service-Linked Roles:**
```bash
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com
```

### Compliance and Auditing

**1. Enable CloudTrail:**
```bash
aws cloudtrail create-trail \
  --name modresorts-trail \
  --s3-bucket-name <bucket-name>

aws cloudtrail start-logging --name modresorts-trail
```

**2. Enable AWS Config:**
Monitor ECS configuration changes and compliance.

**3. Container Image Scanning:**
```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name modresorts \
  --image-scanning-configuration scanOnPush=true
```

### Data Encryption

**1. Encryption at Rest:**
- ECS task storage is encrypted by default
- Use encrypted EBS volumes for persistent data

**2. Encryption in Transit:**
- Use HTTPS for ALB listeners
- Configure SSL/TLS certificates

```bash
# Create HTTPS listener
aws elbv2 create-listener \
  --load-balancer-arn <alb-arn> \
  --protocol HTTPS \
  --port 443 \
  --certificates CertificateArn=<cert-arn> \
  --default-actions Type=forward,TargetGroupArn=<tg-arn>
```

---

## Configuration Management

### Environment-Specific Configuration

**Development:**
```json
{
  "environment": [
    {"name": "APP_ENV", "value": "development"},
    {"name": "LOG_LEVEL", "value": "DEBUG"}
  ]
}
```

**Production:**
```json
{
  "environment": [
    {"name": "APP_ENV", "value": "production"},
    {"name": "LOG_LEVEL", "value": "INFO"}
  ]
}
```

### External Configuration

**Using AWS Systems Manager Parameter Store:**

```bash
# Store parameter
aws ssm put-parameter \
  --name /modresorts/db-host \
  --value "database.example.com" \
  --type String

# Reference in task definition
{
  "secrets": [
    {
      "name": "DB_HOST",
      "valueFrom": "arn:aws:ssm:region:account-id:parameter/modresorts/db-host"
    }
  ]
}
```

---

## Cost Optimization

### Fargate Pricing

Fargate charges based on:
- vCPU per hour
- Memory per GB per hour

**Cost Calculation Example:**
- CPU: 0.5 vCPU = $0.04048 per hour
- Memory: 1 GB = $0.004445 per hour
- Total per task per hour: ~$0.045
- Monthly cost (2 tasks, 24/7): ~$65

### Cost Optimization Strategies

**1. Right-Size Resources:**
- Monitor CPU and memory utilization
- Adjust task definition to match actual usage
- Use smaller CPU/memory combinations when possible

**2. Use Spot Capacity (Fargate Spot):**
```json
{
  "capacityProviderStrategy": [
    {
      "capacityProvider": "FARGATE_SPOT",
      "weight": 1,
      "base": 0
    }
  ]
}
```

**3. Implement Auto Scaling:**
- Scale down during off-peak hours
- Use scheduled scaling for predictable patterns

**4. Optimize Container Image:**
- Use multi-stage builds
- Minimize image size
- Remove unnecessary dependencies

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)

### Best Practices
- [ECS Best Practices Guide](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Java Container Best Practices](https://developers.redhat.com/blog/2017/03/14/java-inside-docker)

### Support
- AWS Support: https://console.aws.amazon.com/support/
- ECS Forum: https://forums.aws.amazon.com/forum.jspa?forumID=187
- GitHub Issues: [Repository Issues Page]

---

## Appendix

### A. Complete Deployment Checklist

- [ ] AWS CLI installed and configured
- [ ] Docker installed and running
- [ ] VPC and subnets created
- [ ] Security groups configured
- [ ] IAM roles created (ecsTaskExecutionRole, ecsTaskRole)
- [ ] ECR repository created
- [ ] Docker image built and pushed
- [ ] CloudWatch log group created
- [ ] Task definition registered
- [ ] ECS cluster created
- [ ] ECS service created
- [ ] Load balancer configured (if needed)
- [ ] Health checks passing
- [ ] Application accessible
- [ ] Monitoring and alerts configured

### B. Environment Variables Reference

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| JAVA_OPTS | JVM options | -Xmx512m -Xms256m | No |
| CATALINA_OPTS | Tomcat options | -Duser.timezone=UTC | No |
| APP_ENV | Environment name | production | No |
| APP_VERSION | Application version | 2.0.0 | No |

### C. Port Reference

| Port | Protocol | Purpose |
|------|----------|---------|
| 8080 | HTTP | Application port |
| 80 | HTTP | Load balancer port |
| 443 | HTTPS | Secure load balancer port |

### D. Health Check Endpoints

| Endpoint | Method | Response | Purpose |
|----------|--------|----------|---------|
| /health | GET | JSON | Container health check |
| /actuator/health | GET | JSON | Alternative health endpoint |

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Application Version:** 2.0.0
