#!/bin/bash

# Build and Push Script for ModResorts Application
# This script builds the Docker image and pushes it to the selected registry

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}ModResorts - Docker Build & Push Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Project configuration
PROJECT_NAME="modresorts"
PROJECT_VERSION="2.0.0"

# Sanitize project name for Docker tag (lowercase, hyphenate)
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo -e "${YELLOW}Select Registry Type:${NC}"
echo "1. AWS ECR (Elastic Container Registry)"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    # AWS ECR Configuration
    echo -e "\n${YELLOW}AWS ECR Configuration${NC}"
    read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
    read -p "Enter AWS Account ID: " AWS_ACCOUNT_ID
    read -p "Enter ECR Repository Name [$IMAGE_NAME]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    
    # Construct registry URL
    REGISTRY_URL="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
    
    echo -e "\n${GREEN}Authenticating with AWS ECR...${NC}"
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}ECR authentication failed. Please check your AWS credentials.${NC}"
        exit 1
    fi
    
    # Check if repository exists, create if not
    echo -e "${GREEN}Checking ECR repository...${NC}"
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
        echo -e "${YELLOW}Repository does not exist. Creating...${NC}"
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
        echo -e "${GREEN}Repository created successfully.${NC}"
    }
    
    # Prompt for image tag
    read -p "Enter image tag [latest]: " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    FULL_IMAGE_NAME="$REGISTRY_URL/$ECR_REPO:$IMAGE_TAG"
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    # Docker Hub Configuration
    echo -e "\n${YELLOW}Docker Hub Configuration${NC}"
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub Password/Token: " DOCKER_PASSWORD
    echo ""
    
    echo -e "\n${GREEN}Authenticating with Docker Hub...${NC}"
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Docker Hub authentication failed.${NC}"
        exit 1
    fi
    
    # Prompt for image tag
    read -p "Enter image tag [latest]: " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    FULL_IMAGE_NAME="$DOCKER_USERNAME/$IMAGE_NAME:$IMAGE_TAG"
    
else
    echo -e "${RED}Invalid choice. Exiting.${NC}"
    exit 1
fi

# Build Docker image
echo -e "\n${GREEN}Building Docker image: $FULL_IMAGE_NAME${NC}"
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed.${NC}"
    exit 1
fi

echo -e "${GREEN}Docker image built successfully.${NC}"

# Push Docker image
echo -e "\n${GREEN}Pushing Docker image to registry...${NC}"
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker push failed.${NC}"
    exit 1
fi

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Build and Push Completed Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Image: ${YELLOW}$FULL_IMAGE_NAME${NC}"
echo ""
echo -e "Next steps:"
echo -e "1. Update ECS task definition with image URI: ${YELLOW}$FULL_IMAGE_NAME${NC}"
echo -e "2. Run deployment script: ${YELLOW}./scripts/deploy-image.sh${NC}"
echo ""
