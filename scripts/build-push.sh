#!/bin/bash

# Build and Push Script for ModResorts Application
# Supports AWS ECR and Docker Hub registries

set -e

echo "=========================================="
echo "ModResorts - Docker Build and Push Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="modresorts"
IMAGE_TAG_INPUT=""

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG_INPUT
IMAGE_TAG="${IMAGE_TAG_INPUT:-latest}"

# Sanitize image tag: lowercase, replace invalid chars with hyphens, trim leading/trailing hyphens
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')

# Default to 'latest' if tag becomes empty after sanitization
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
fi

echo ""
echo "Using image tag: $IMAGE_TAG"
echo ""

# Registry selection
echo "Select container registry:"
echo "1. AWS ECR (Elastic Container Registry)"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

case $REGISTRY_CHOICE in
    1)
        echo ""
        echo "=== AWS ECR Configuration ==="
        read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
        read -p "Enter AWS Account ID: " AWS_ACCOUNT_ID
        read -p "Enter ECR Repository Name (default: modresorts): " ECR_REPO_INPUT
        ECR_REPO="${ECR_REPO_INPUT:-modresorts}"
        
        # Sanitize ECR repository name
        ECR_REPO=$(echo "$ECR_REPO" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9/_-' '-' | sed 's/^-*//;s/-*$//')
        
        REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
        
        echo ""
        echo "Authenticating with AWS ECR..."
        aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
        
        if [ $? -ne 0 ]; then
            echo "ERROR: ECR authentication failed"
            exit 1
        fi
        
        echo "Checking if ECR repository exists..."
        aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
            echo "Repository does not exist. Creating ECR repository: $ECR_REPO"
            aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
        }
        ;;
        
    2)
        echo ""
        echo "=== Docker Hub Configuration ==="
        read -p "Enter Docker Hub username: " DOCKER_USERNAME
        read -sp "Enter Docker Hub password or access token: " DOCKER_PASSWORD
        echo ""
        
        # Sanitize image name
        IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
        FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
        
        echo ""
        echo "Authenticating with Docker Hub..."
        echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
        
        if [ $? -ne 0 ]; then
            echo "ERROR: Docker Hub authentication failed"
            exit 1
        fi
        ;;
        
    *)
        echo "ERROR: Invalid choice. Please select 1 or 2."
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "Building Docker image..."
echo "Image: $FULL_IMAGE_NAME"
echo "=========================================="
echo ""

# Build Docker image
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Pushing image to registry..."
echo "=========================================="
echo ""

# Push Docker image
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "SUCCESS!"
echo "=========================================="
echo "Image successfully built and pushed:"
echo "  $FULL_IMAGE_NAME"
echo ""
echo "To deploy to ECS, use the deploy-image.sh script"
echo "=========================================="
