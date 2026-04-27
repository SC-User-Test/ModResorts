#!/bin/bash

# Build and Push Script for ModResorts Application
# This script builds the Docker image and pushes it to a container registry
# Supports AWS ECR and Docker Hub

set -e  # Exit on error
set -o pipefail  # Exit on pipe failure

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

# Sanitize image name: lowercase, replace non-alphanumeric with hyphens, trim hyphens
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo -e "${YELLOW}Project:${NC} $PROJECT_NAME"
echo -e "${YELLOW}Version:${NC} $PROJECT_VERSION"
echo -e "${YELLOW}Sanitized Image Name:${NC} $IMAGE_NAME"
echo ""

# Prompt for registry selection
echo -e "${YELLOW}Select Container Registry:${NC}"
echo "1. AWS ECR (Elastic Container Registry)"
echo "2. Docker Hub"
read -p "Enter your choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    # AWS ECR Configuration
    echo ""
    echo -e "${GREEN}=== AWS ECR Configuration ===${NC}"
    
    read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
    read -p "Enter AWS Account ID: " AWS_ACCOUNT_ID
    read -p "Enter ECR Repository Name [$IMAGE_NAME]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    
    # Construct ECR registry URL
    REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    
    echo ""
    echo -e "${YELLOW}Authenticating with AWS ECR...${NC}"
    
    # Login to ECR
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to authenticate with AWS ECR${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Successfully authenticated with AWS ECR${NC}"
    
    # Check if repository exists, create if it doesn't
    echo -e "${YELLOW}Checking if ECR repository exists...${NC}"
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
        echo -e "${YELLOW}Repository does not exist. Creating ECR repository: $ECR_REPO${NC}"
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
        echo -e "${GREEN}ECR repository created successfully${NC}"
    }
    
    # Prompt for image tag
    read -p "Enter image tag [latest]: " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag: lowercase, replace non-alphanumeric with hyphens, trim hyphens
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    
    # Default to 'latest' if tag is empty after sanitization
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi
    
    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    # Docker Hub Configuration
    echo ""
    echo -e "${GREEN}=== Docker Hub Configuration ===${NC}"
    
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub Password/Token: " DOCKER_PASSWORD
    echo ""
    
    echo -e "${YELLOW}Authenticating with Docker Hub...${NC}"
    
    # Login to Docker Hub
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to authenticate with Docker Hub${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Successfully authenticated with Docker Hub${NC}"
    
    # Prompt for image tag
    read -p "Enter image tag [latest]: " IMAGE_TAG
    IMAGE_TAG=${IMAGE_TAG:-latest}
    
    # Sanitize tag
    IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')
    
    # Default to 'latest' if tag is empty
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi
    
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    
else
    echo -e "${RED}Invalid choice. Exiting.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=== Building Docker Image ===${NC}"
echo -e "${YELLOW}Image Name:${NC} $FULL_IMAGE_NAME"
echo ""

# Build the Docker image
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Docker image built successfully!${NC}"
echo ""

# Push the image to registry
echo -e "${GREEN}=== Pushing Image to Registry ===${NC}"
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker push failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build and Push Completed Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Image:${NC} $FULL_IMAGE_NAME"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Update your deployment manifests with the image URI"
echo "2. Deploy to your target environment (ECS, Kubernetes, etc.)"
echo ""
