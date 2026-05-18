#!/bin/bash

# ECS Fargate Deployment Script for ModResorts Application
# This script deploys the containerized application to AWS ECS Fargate

set -e  # Exit on any error
set -o pipefail  # Exit on pipe failures

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}ModResorts - ECS Fargate Deployment${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Configuration
PROJECT_NAME="modresorts"
TASK_FAMILY="modresorts-task"
SERVICE_NAME="modresorts-service"

# Prompt for AWS configuration
echo -e "${YELLOW}AWS Configuration${NC}"
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter ECS Cluster Name: " CLUSTER_NAME

# Get AWS Account ID
echo -e "\n${GREEN}Retrieving AWS Account ID...${NC}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo -e "Account ID: ${BLUE}$ACCOUNT_ID${NC}"

# Check if cluster exists, create if it doesn't
echo -e "\n${GREEN}Checking ECS cluster...${NC}"
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo -e "${YELLOW}Cluster does not exist. Creating ECS cluster: $CLUSTER_NAME${NC}"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
}

# Prompt for network configuration
echo -e "\n${YELLOW}Network Configuration${NC}"
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP

# Parse subnets
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

# Prompt for image URI
echo -e "\n${YELLOW}Container Image Configuration${NC}"
read -p "Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI

# Ask about load balancer
echo -e "\n${YELLOW}Load Balancer Configuration${NC}"
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo -e "${GREEN}Creating Application Load Balancer and Target Group...${NC}"
    
    # Create ALB
    ALB_NAME="${PROJECT_NAME}-alb"
    echo -e "Creating ALB: ${BLUE}$ALB_NAME${NC}"
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
    
    echo -e "ALB ARN: ${BLUE}$ALB_ARN${NC}"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    # Create Target Group with target-type ip (required for Fargate)
    TG_NAME="${PROJECT_NAME}-tg"
    echo -e "Creating Target Group: ${BLUE}$TG_NAME${NC}"
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
    
    echo -e "Target Group ARN: ${BLUE}$TARGET_GROUP_ARN${NC}"
    
    # Create Listener
    echo -e "Creating ALB Listener..."
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" >/dev/null
    
    echo -e "${GREEN}Load Balancer created successfully!${NC}"
    echo -e "ALB DNS: ${BLUE}$ALB_DNS${NC}"
else
    echo -e "${YELLOW}Skipping load balancer creation${NC}"
    TARGET_GROUP_ARN=""
fi

# Create CloudWatch Log Group
echo -e "\n${GREEN}Creating CloudWatch Log Group...${NC}"
LOG_GROUP="/ecs/$PROJECT_NAME"
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION" 2>/dev/null || echo "Log group already exists"

# Prepare task definition
echo -e "\n${GREEN}Preparing ECS Task Definition...${NC}"
TASK_DEF_FILE="ecs/task-definition.json"
TASK_DEF_TEMP="/tmp/task-definition-$$.json"

# Replace placeholders in task definition
sed "s|{{IMAGE_URI}}|$IMAGE_URI|g; s|{{AWS_REGION}}|$AWS_REGION|g; s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g" "$TASK_DEF_FILE" > "$TASK_DEF_TEMP"

# Register task definition
echo -e "${GREEN}Registering ECS Task Definition...${NC}"
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TASK_DEF_TEMP" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo -e "Task Definition ARN: ${BLUE}$TASK_DEF_ARN${NC}"

# Clean up temp file
rm -f "$TASK_DEF_TEMP"

# Prepare service definition
echo -e "\n${GREEN}Preparing ECS Service Definition...${NC}"
SERVICE_DEF_FILE="ecs/service-definition.json"
SERVICE_DEF_TEMP="/tmp/service-definition-$$.json"

# Replace placeholders in service definition
sed "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g; s|{{SUBNET_1}}|$SUBNET_1|g; s|{{SUBNET_2}}|$SUBNET_2|g; s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g; s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" "$SERVICE_DEF_FILE" > "$SERVICE_DEF_TEMP"

# If no load balancer, remove loadBalancers section
if [[ ! "$NEED_LB" =~ ^[Yy]$ ]]; then
    # Remove loadBalancers and healthCheckGracePeriodSeconds from service definition
    python3 -c "
import json
import sys
with open('$SERVICE_DEF_TEMP', 'r') as f:
    data = json.load(f)
data.pop('loadBalancers', None)
data.pop('healthCheckGracePeriodSeconds', None)
with open('$SERVICE_DEF_TEMP', 'w') as f:
    json.dump(data, f, indent=2)
" 2>/dev/null || {
    # Fallback if python3 is not available
    jq 'del(.loadBalancers, .healthCheckGracePeriodSeconds)' "$SERVICE_DEF_TEMP" > "${SERVICE_DEF_TEMP}.tmp" && mv "${SERVICE_DEF_TEMP}.tmp" "$SERVICE_DEF_TEMP"
}
fi

# Check if service exists
echo -e "\n${GREEN}Checking if ECS service exists...${NC}"
SERVICE_EXISTS=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].serviceName' \
    --output text 2>/dev/null || echo "None")

if [ "$SERVICE_EXISTS" == "None" ] || [ "$SERVICE_EXISTS" == "" ]; then
    # Create new service
    echo -e "${GREEN}Creating new ECS service: $SERVICE_NAME${NC}"
    aws ecs create-service \
        --cli-input-json file://"$SERVICE_DEF_TEMP" \
        --region "$AWS_REGION" >/dev/null
else
    # Update existing service
    echo -e "${GREEN}Updating existing ECS service: $SERVICE_NAME${NC}"
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "$SERVICE_NAME" \
        --task-definition "$TASK_DEF_ARN" \
        --desired-count 2 \
        --force-new-deployment \
        --region "$AWS_REGION" >/dev/null
fi

# Clean up temp file
rm -f "$SERVICE_DEF_TEMP"

# Wait for service to stabilize
echo -e "\n${GREEN}Waiting for service to stabilize (this may take a few minutes)...${NC}"
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION"

# Verify deployment
echo -e "\n${GREEN}Verifying deployment...${NC}"
RUNNING_COUNT=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$AWS_REGION" \
    --query 'services[0].runningCount' \
    --output text)

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment Completed Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Cluster: ${BLUE}$CLUSTER_NAME${NC}"
echo -e "Service: ${BLUE}$SERVICE_NAME${NC}"
echo -e "Running Tasks: ${BLUE}$RUNNING_COUNT${NC}"
echo -e "CloudWatch Logs: ${BLUE}$LOG_GROUP${NC}"

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
    echo -e "Load Balancer DNS: ${BLUE}$ALB_DNS${NC}"
    echo -e "\n${YELLOW}Access your application at: http://$ALB_DNS${NC}"
fi

echo -e "\n${YELLOW}Useful Commands:${NC}"
echo -e "View service: ${BLUE}aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $AWS_REGION${NC}"
echo -e "View tasks: ${BLUE}aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --region $AWS_REGION${NC}"
echo -e "View logs: ${BLUE}aws logs tail $LOG_GROUP --follow --region $AWS_REGION${NC}"
echo ""
