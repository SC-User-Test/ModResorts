# ModResorts Application - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Fargate Deployment](#ecs-fargate-deployment)
7. [Configuration Management](#configuration-management)
8. [Monitoring and Logging](#monitoring-and-logging)
9. [Troubleshooting](#troubleshooting)
10. [Security Considerations](#security-considerations)

---

## Overview

ModResorts is a Java EE 7 web application packaged as a WAR file. This guide covers containerization and deployment to AWS ECS Fargate using Docker and Tomcat 9.

**Application Details:**
- **Technology**: Java EE 7 (Servlets, JSP)
- **Java Version**: Java 8
- **Build Tool**: Maven
- **Package Type**: WAR
- **Application Server**: Apache Tomcat 9.0
- **Application Port**: 8080
- **Health Endpoint**: `/health` and `/actuator/health`

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 1.29 or higher
- **AWS CLI**: Version 2.x
- **Maven**: Version 3.6 or higher (for local builds)
- **Java JDK**: Version 8 or higher
- **Git**: For version control

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECS (Full access)
  - ECR (Full access)
  - CloudWatch Logs (Write access)
  - VPC (Read access)
  - IAM (Role creation/management)
  - Elastic Load Balancing (if using ALB)

### Install AWS CLI
```bash
# Linux/macOS
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download and run the AWS CLI MSI installer from:
# https://awscli.amazonaws.com/AWSCLIV2.msi

# Configure AWS CLI
aws configure
# Enter your AWS Access Key ID, Secret Access Key, Region, and output format
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd AppTestMODResComp
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
# Application: http://localhost:8080
# Health Check: http://localhost:8080/health

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

The script will prompt you to:
1. Select registry type (AWS ECR or Docker Hub)
2. Enter registry credentials and details
3. Enter image tag (default: latest)

### Option 2: Manual Build and Push

#### AWS ECR
```bash
# Set variables
AWS_REGION="us-east-1"
AWS_ACCOUNT_ID="123456789012"
ECR_REPO="modresorts"
IMAGE_TAG="latest"

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
docker build -t your-username/modresorts:latest .

# Push
docker push your-username/modresorts:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. VPC and Networking Setup

#### Create VPC (if needed)
```bash
# Create VPC
VPC_ID=$(aws ec2 create-vpc \
  --cidr-block 10.0.0.0/16 \
  --region us-east-1 \
  --query 'Vpc.VpcId' \
  --output text)

# Enable DNS hostnames
aws ec2 modify-vpc-attribute \
  --vpc-id $VPC_ID \
  --enable-dns-hostnames

# Create Internet Gateway
IGW_ID=$(aws ec2 create-internet-gateway \
  --region us-east-1 \
  --query 'InternetGateway.InternetGatewayId' \
  --output text)

# Attach Internet Gateway to VPC
aws ec2 attach-internet-gateway \
  --vpc-id $VPC_ID \
  --internet-gateway-id $IGW_ID
```

#### Create Subnets
```bash
# Create public subnet 1 (us-east-1a)
SUBNET_1=$(aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.1.0/24 \
  --availability-zone us-east-1a \
  --query 'Subnet.SubnetId' \
  --output text)

# Create public subnet 2 (us-east-1b)
SUBNET_2=$(aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.2.0/24 \
  --availability-zone us-east-1b \
  --query 'Subnet.SubnetId' \
  --output text)

# Enable auto-assign public IP
aws ec2 modify-subnet-attribute \
  --subnet-id $SUBNET_1 \
  --map-public-ip-on-launch

aws ec2 modify-subnet-attribute \
  --subnet-id $SUBNET_2 \
  --map-public-ip-on-launch

# Create route table
ROUTE_TABLE=$(aws ec2 create-route-table \
  --vpc-id $VPC_ID \
  --query 'RouteTable.RouteTableId' \
  --output text)

# Add route to Internet Gateway
aws ec2 create-route \
  --route-table-id $ROUTE_TABLE \
  --destination-cidr-block 0.0.0.0/0 \
  --gateway-id $IGW_ID

# Associate subnets with route table
aws ec2 associate-route-table \
  --subnet-id $SUBNET_1 \
  --route-table-id $ROUTE_TABLE

aws ec2 associate-route-table \
  --subnet-id $SUBNET_2 \
  --route-table-id $ROUTE_TABLE
```

#### Create Security Group
```bash
# Create security group
SG_ID=$(aws ec2 create-security-group \
  --group-name modresorts-sg \
  --description "Security group for ModResorts ECS tasks" \
  --vpc-id $VPC_ID \
  --query 'GroupId' \
  --output text)

# Allow inbound HTTP traffic (port 8080)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow inbound HTTP traffic (port 80) for ALB
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0

# Allow all outbound traffic (default)
```

### 2. IAM Roles Setup

#### Create ECS Task Execution Role
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

#### Create ECS Task Role (Optional)
```bash
# Create task role for application permissions
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json

# Attach policies as needed (e.g., S3, DynamoDB access)
# Example: S3 read access
aws iam attach-role-policy \
  --role-name ecsTaskRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess
```

### 3. CloudWatch Logs Setup
```bash
# Create log group
aws logs create-log-group \
  --log-group-name /ecs/modresorts \
  --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/modresorts \
  --retention-in-days 7
```

---

## ECS Fargate Deployment

### Deployment Methods

#### Method 1: Using Deployment Script (Recommended)

##### Linux/macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

##### Windows
```cmd
scripts\deploy-image.bat
```

The script will:
1. Prompt for AWS region and ECS cluster name
2. Create cluster if it doesn't exist
3. Prompt for network configuration (VPC, subnets, security group)
4. Prompt for Docker image URI
5. Ask if you need a load balancer
6. Create ALB and Target Group (if requested)
7. Register task definition
8. Create or update ECS service
9. Wait for service to stabilize
10. Display deployment status and URLs

#### Method 2: Manual Deployment

##### Step 1: Create ECS Cluster
```bash
aws ecs create-cluster \
  --cluster-name modresorts-cluster \
  --region us-east-1
```

##### Step 2: Register Task Definition
```bash
# Update ecs/task-definition.json with your values:
# - Replace {{IMAGE_URI}} with your image URI
# - Replace {{AWS_REGION}} with your region
# - Replace {{ACCOUNT_ID}} with your AWS account ID

aws ecs register-task-definition \
  --cli-input-json file://ecs/task-definition.json \
  --region us-east-1
```

##### Step 3: Create ECS Service
```bash
# Update ecs/service-definition.json with your values:
# - Replace {{CLUSTER_NAME}} with your cluster name
# - Replace {{SUBNET_1}} and {{SUBNET_2}} with your subnet IDs
# - Replace {{SECURITY_GROUP}} with your security group ID
# - Replace {{TARGET_GROUP_ARN}} with your target group ARN (if using ALB)

aws ecs create-service \
  --cli-input-json file://ecs/service-definition.json \
  --region us-east-1
```

##### Step 4: Wait for Service Stability
```bash
aws ecs wait services-stable \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1
```

### Understanding ECS Fargate Resources

#### CPU and Memory Combinations
Fargate requires specific CPU/memory combinations:

| CPU (vCPU) | Memory (MB) Options |
|------------|---------------------|
| 0.25 (256) | 512, 1024, 2048 |
| 0.5 (512)  | 1024, 2048, 3072, 4096 |
| 1 (1024)   | 2048-8192 (increments of 1024) |
| 2 (2048)   | 4096-16384 (increments of 1024) |
| 4 (4096)   | 8192-30720 (increments of 1024) |

**Default Configuration**: CPU: 512 (0.5 vCPU), Memory: 1024 MB

#### Task Definition Components
- **Family**: Task definition name (modresorts-task)
- **Network Mode**: awsvpc (required for Fargate)
- **Execution Role**: IAM role for ECS to pull images and write logs
- **Task Role**: IAM role for application permissions
- **Container Definitions**: Application container configuration

#### Service Configuration
- **Launch Type**: FARGATE
- **Desired Count**: Number of tasks to run (default: 2)
- **Network Configuration**: VPC, subnets, security groups
- **Load Balancer**: Optional ALB/NLB integration
- **Deployment Configuration**: Rolling update settings

---

## Configuration Management

### Environment Variables

Environment variables can be configured in the task definition:

```json
"environment": [
  {
    "name": "JAVA_OPTS",
    "value": "-Xmx512m -Xms256m -XX:+UseContainerSupport"
  },
  {
    "name": "CATALINA_OPTS",
    "value": "-Duser.timezone=UTC"
  },
  {
    "name": "DB_HOST",
    "value": "your-database-host"
  }
]
```

### Secrets Management

For sensitive data, use AWS Secrets Manager:

```json
"secrets": [
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:region:account-id:secret:db-password"
  }
]
```

### Application Configuration Files

Mount configuration files using EFS (Elastic File System):

1. Create EFS file system
2. Mount EFS to ECS tasks
3. Store configuration files in EFS

---

## Monitoring and Logging

### CloudWatch Logs

View application logs:
```bash
# Tail logs
aws logs tail /ecs/modresorts --follow --region us-east-1

# Filter logs
aws logs filter-log-events \
  --log-group-name /ecs/modresorts \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### CloudWatch Metrics

Monitor ECS metrics:
- CPU Utilization
- Memory Utilization
- Network In/Out
- Task Count

```bash
# Get CPU utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=modresorts-service Name=ClusterName,Value=modresorts-cluster \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600 \
  --statistics Average \
  --region us-east-1
```

### Application Health Checks

The application provides health check endpoints:
- **Primary**: `/health`
- **Alternative**: `/actuator/health`

Health check response:
```json
{
  "status": "UP",
  "application": "ModResorts",
  "version": "2.0.0"
}
```

### Load Balancer Health Checks

If using ALB, configure target group health checks:
- **Protocol**: HTTP
- **Path**: `/health`
- **Interval**: 30 seconds
- **Timeout**: 5 seconds
- **Healthy Threshold**: 2
- **Unhealthy Threshold**: 3

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms**: Tasks transition to STOPPED state immediately

**Possible Causes**:
- Invalid CPU/memory combination
- Image pull errors
- Missing IAM permissions
- Network configuration issues

**Solutions**:
```bash
# Check task stopped reason
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1 \
  --query 'tasks[0].stoppedReason'

# Check CloudWatch logs for errors
aws logs tail /ecs/modresorts --follow --region us-east-1

# Verify IAM role permissions
aws iam get-role --role-name ecsTaskExecutionRole
```

#### 2. Cannot Pull Docker Image

**Symptoms**: "CannotPullContainerError"

**Solutions**:
- Verify ECR repository exists and image is pushed
- Check execution role has ECR permissions
- Ensure image URI is correct in task definition

```bash
# List ECR images
aws ecr list-images \
  --repository-name modresorts \
  --region us-east-1

# Test ECR authentication
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com
```

#### 3. Service Not Reaching Steady State

**Symptoms**: Service stuck in deployment, tasks failing health checks

**Solutions**:
- Check application logs for startup errors
- Verify health check endpoint is accessible
- Increase health check grace period
- Check security group allows traffic on port 8080

```bash
# Describe service events
aws ecs describe-services \
  --cluster modresorts-cluster \
  --services modresorts-service \
  --region us-east-1 \
  --query 'services[0].events[0:10]'

# Test health endpoint from within VPC
curl http://<task-private-ip>:8080/health
```

#### 4. Out of Memory Errors

**Symptoms**: Tasks killed due to memory exhaustion

**Solutions**:
- Increase task memory allocation
- Adjust JVM heap size in JAVA_OPTS
- Monitor memory usage with CloudWatch

```bash
# Update task definition with more memory
# Edit ecs/task-definition.json:
# "memory": "2048"  # Increase from 1024

# Adjust JVM settings
# "JAVA_OPTS": "-Xmx1536m -Xms512m"
```

#### 5. Network Connectivity Issues

**Symptoms**: Cannot access external services, database connections fail

**Solutions**:
- Verify security group allows outbound traffic
- Check NAT Gateway for private subnets
- Verify DNS resolution

```bash
# Test connectivity from task
aws ecs execute-command \
  --cluster modresorts-cluster \
  --task <task-id> \
  --container modresorts \
  --interactive \
  --command "/bin/bash"

# Inside container:
curl -v http://external-service.com
nslookup database-host
```

### Useful Commands

```bash
# List running tasks
aws ecs list-tasks \
  --cluster modresorts-cluster \
  --service-name modresorts-service \
  --region us-east-1

# Describe task details
aws ecs describe-tasks \
  --cluster modresorts-cluster \
  --tasks <task-id> \
  --region us-east-1

# Update service (force new deployment)
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force-new-deployment \
  --region us-east-1

# Scale service
aws ecs update-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --desired-count 4 \
  --region us-east-1

# Stop task
aws ecs stop-task \
  --cluster modresorts-cluster \
  --task <task-id> \
  --region us-east-1

# Delete service
aws ecs delete-service \
  --cluster modresorts-cluster \
  --service modresorts-service \
  --force \
  --region us-east-1

# Delete cluster
aws ecs delete-cluster \
  --cluster modresorts-cluster \
  --region us-east-1
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Dockerfile creates and uses non-root user `appuser`
- **Minimal Base Image**: Uses official Tomcat image with JRE only
- **No Unnecessary Tools**: Runtime image doesn't include curl, wget, or other tools
- **Read-only Root Filesystem**: Consider enabling in task definition

### 2. Network Security

- **Security Groups**: Restrict inbound traffic to necessary ports only
- **Private Subnets**: Use private subnets with NAT Gateway for production
- **VPC Endpoints**: Use VPC endpoints for AWS services (ECR, CloudWatch, Secrets Manager)
- **TLS/SSL**: Enable HTTPS on load balancer

### 3. IAM Security

- **Least Privilege**: Grant minimum required permissions
- **Separate Roles**: Use different roles for execution and task
- **No Hardcoded Credentials**: Use IAM roles and Secrets Manager
- **Regular Audits**: Review and rotate credentials regularly

### 4. Application Security

- **Environment Variables**: Use Secrets Manager for sensitive data
- **Input Validation**: Validate all user inputs
- **Security Headers**: Configure security headers in Tomcat
- **Regular Updates**: Keep dependencies and base images updated

### 5. Monitoring and Auditing

- **CloudWatch Logs**: Enable and monitor application logs
- **CloudTrail**: Enable for API call auditing
- **GuardDuty**: Enable for threat detection
- **Security Hub**: Enable for security posture management

### Best Practices

1. **Use Private ECR Repositories**: Keep images private
2. **Scan Images**: Use ECR image scanning for vulnerabilities
3. **Rotate Secrets**: Regularly rotate database passwords and API keys
4. **Enable Encryption**: Encrypt data at rest and in transit
5. **Implement WAF**: Use AWS WAF with ALB for web application protection
6. **Regular Backups**: Backup application data and configurations
7. **Disaster Recovery**: Implement multi-region deployment for critical applications

---

## Scaling and Performance

### Auto Scaling

Configure ECS Service Auto Scaling:

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1

# Create scaling policy (CPU-based)
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/modresorts-cluster/modresorts-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json \
  --region us-east-1
```

scaling-policy.json:
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

### Performance Tuning

#### JVM Tuning
```bash
# Optimize for container environment
JAVA_OPTS="-Xmx512m -Xms256m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled"
```

#### Tomcat Tuning
```xml
<!-- server.xml -->
<Connector port="8080" protocol="HTTP/1.1"
           maxThreads="200"
           minSpareThreads="25"
           connectionTimeout="20000"
           enableLookups="false"
           acceptCount="100"
           compression="on"
           compressionMinSize="2048"/>
```

---

## Additional Resources

- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [Docker Documentation](https://docs.docker.com/)
- [Apache Tomcat Documentation](https://tomcat.apache.org/tomcat-9.0-doc/)
- [Java Performance Tuning](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)

---

## Support and Contribution

For issues, questions, or contributions, please refer to the project repository.

**Version**: 2.0.0  
**Last Updated**: 2024  
**Deployment Platform**: AWS ECS Fargate
