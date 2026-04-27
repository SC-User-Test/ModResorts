#!/bin/bash

# ECS Fargate Deployment Script for ModResorts Application
# This script deploys the containerized application to AWS ECS Fargate

set -e  # Exit on error
set -o pipefail  # Exit on pipe failure

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}ModResorts - ECS Fargate Deployment Script${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Project configuration
PROJECT_NAME="modresorts"
SERVICE_NAME="modresorts-service"
TASK_FAMILY="modresorts-task"

# Prompt for AWS configuration
echo -e "${BLUE}=== AWS Configuration ===${NC}"
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
export AWS_DEFAULT_REGION="$AWS_REGION"

read -p "Enter ECS Cluster Name: " CLUSTER_NAME

# Check if cluster exists, create if it doesn't
echo -e "${YELLOW}Checking if ECS cluster exists...${NC}"
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo -e "${YELLOW}Cluster does not exist. Creating ECS cluster: $CLUSTER_NAME${NC}"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
    echo -e "${GREEN}ECS cluster created successfully${NC}"
}

echo ""
echo -e "${BLUE}=== Network Configuration ===${NC}"
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP

# Parse subnets
IFS=',' read -ra SUBNET_ARRAY <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNET_ARRAY[0]}" | xargs)
SUBNET_2=$(echo "${SUBNET_ARRAY[1]}" | xargs)

echo ""
echo -e "${BLUE}=== Container Image Configuration ===${NC}"
read -p "Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI

# Get AWS Account ID
echo -e "${YELLOW}Retrieving AWS Account ID...${NC}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo -e "${GREEN}Account ID: $ACCOUNT_ID${NC}"

echo ""
echo -e "${BLUE}=== Load Balancer Configuration ===${NC}"
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Creating Application Load Balancer and Target Group...${NC}"
    
    # Create ALB
    ALB_NAME="${PROJECT_NAME}-alb"
    echo -e "${YELLOW}Creating Application Load Balancer: $ALB_NAME${NC}"
    
    ALB_ARN=$(aws elbv2 create-load-balancer \
        --name "$ALB_NAME" \
        --subnets "$SUBNET_1" "$SUBNET_2" \
        --security-groups "$SECURITY_GROUP" \
        --scheme internet-facing \
        --type application \
        --ip-address-type ipv4 \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].LoadBalancerArn' \
        --output text)
    
    echo -e "${GREEN}ALB created: $ALB_ARN${NC}"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    # Create Target Group with target-type ip (required for Fargate)
    TG_NAME="${PROJECT_NAME}-tg"
    echo -e "${YELLOW}Creating Target Group: $TG_NAME${NC}"
    
    TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
        --name "$TG_NAME" \
        --protocol HTTP \
        --port 8080 \
        --vpc-id "$VPC_ID" \
        --target-type ip \
        --health-check-enabled \
        --health-check-protocol HTTP \
        --health-check-path "/health" \
        --health-check-interval-seconds 30 \
        --health-check-timeout-seconds 5 \
        --healthy-threshold-count 2 \
        --unhealthy-threshold-count 3 \
        --region "$AWS_REGION" \
        --query 'TargetGroups[0].TargetGroupArn' \
        --output text)
    
    echo -e "${GREEN}Target Group created: $TARGET_GROUP_ARN${NC}"
    
    # Create Listener
    echo -e "${YELLOW}Creating ALB Listener...${NC}"
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" >/dev/null
    
    echo -e "${GREEN}ALB Listener created${NC}"
    
    # Update service definition with load balancer configuration
    TEMP_SERVICE_DEF="/tmp/service-definition-temp.json"
    cp ecs/service-definition.json "$TEMP_SERVICE_DEF"
else
    # Remove load balancer section from service definition
    echo -e "${YELLOW}Removing load balancer configuration from service definition...${NC}"
    TEMP_SERVICE_DEF="/tmp/service-definition-temp.json"
    jq 'del(.loadBalancers) | del(.healthCheckGracePeriodSeconds)' ecs/service-definition.json > "$TEMP_SERVICE_DEF"
fi

echo ""
echo -e "${BLUE}=== Preparing Deployment Manifests ===${NC}"

# Create temporary files with replaced placeholders
TEMP_TASK_DEF="/tmp/task-definition-temp.json"

# Replace placeholders in task definition
sed "s|{{IMAGE_URI}}|$IMAGE_URI|g; s|{{AWS_REGION}}|$AWS_REGION|g; s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g" \
    ecs/task-definition.json > "$TEMP_TASK_DEF"

# Replace placeholders in service definition
if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    sed -i "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g; s|{{SUBNET_1}}|$SUBNET_1|g; s|{{SUBNET_2}}|$SUBNET_2|g; s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g; s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" \
        "$TEMP_SERVICE_DEF"
else
    sed -i "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g; s|{{SUBNET_1}}|$SUBNET_1|g; s|{{SUBNET_2}}|$SUBNET_2|g; s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" \
        "$TEMP_SERVICE_DEF"
fi

echo -e "${GREEN}Deployment manifests prepared${NC}"

# Create CloudWatch Log Group
echo ""
echo -e "${YELLOW}Creating CloudWatch Log Group...${NC}"
aws logs create-log-group --log-group-name "/ecs/$PROJECT_NAME" --region "$AWS_REGION" 2>/dev/null || echo -e "${YELLOW}Log group already exists${NC}"

# Register task definition
echo ""
echo -e "${BLUE}=== Registering ECS Task Definition ===${NC}"
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TEMP_TASK_DEF" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo -e "${GREEN}Task definition registered: $TASK_DEF_ARN${NC}"

# Check if service exists
echo ""
echo -e "${YELLOW}Checking if ECS service exists...${NC}"
SERVICE_EXISTS=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].serviceName' \
    --output text 2>/dev/null)

if [ "$SERVICE_EXISTS" == "$SERVICE_NAME" ]; then
    # Update existing service
    echo -e "${YELLOW}Service exists. Updating service...${NC}"
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --region "$AWS_REGION" \
        --force-new-deployment >/dev/null
    
    echo -e "${GREEN}Service updated successfully${NC}"
else
    # Create new service
    echo -e "${YELLOW}Service does not exist. Creating new service...${NC}"
    aws ecs create-service \
        --cli-input-json file://"$TEMP_SERVICE_DEF" \
        --region "$AWS_REGION" >/dev/null
    
    echo -e "${GREEN}Service created successfully${NC}"
fi

# Wait for service to become stable
echo ""
echo -e "${YELLOW}Waiting for service to become stable (this may take a few minutes)...${NC}"
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

echo -e "${GREEN}Service is stable${NC}"

# Verify deployment
echo ""
echo -e "${BLUE}=== Deployment Verification ===${NC}"
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].[serviceName,status,runningCount,desiredCount]' \
    --output table

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}Deployment Completed Successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${YELLOW}Service Details:${NC}"
echo -e "  Cluster: $CLUSTER_NAME"
echo -e "  Service: $SERVICE_NAME"
echo -e "  Task Definition: $TASK_DEF_ARN"
echo -e "  Region: $AWS_REGION"
echo ""

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Load Balancer:${NC}"
    echo -e "  DNS Name: ${GREEN}http://$ALB_DNS${NC}"
    echo -e "  Access your application at: ${GREEN}http://$ALB_DNS${NC}"
    echo ""
fi

echo -e "${YELLOW}CloudWatch Logs:${NC}"
echo -e "  Log Group: /ecs/$PROJECT_NAME"
echo -e "  View logs: aws logs tail /ecs/$PROJECT_NAME --follow --region $AWS_REGION"
echo ""
echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  List tasks: aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --region $AWS_REGION"
echo -e "  Describe service: aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $AWS_REGION"
echo -e "  View logs: aws logs tail /ecs/$PROJECT_NAME --follow --region $AWS_REGION"
echo ""

# Cleanup temporary files
rm -f "$TEMP_TASK_DEF" "$TEMP_SERVICE_DEF"
