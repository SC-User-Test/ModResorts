@echo off
setlocal enabledelayedexpansion

REM Build and Push Script for ModResorts Application (Windows)
REM This script builds the Docker image and pushes it to the selected registry

echo ========================================
echo ModResorts - Docker Build ^& Push Script
echo ========================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts
set PROJECT_VERSION=2.0.0

REM Sanitize project name for Docker tag (lowercase, hyphenate)
set IMAGE_NAME=%PROJECT_NAME%
for %%i in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do (
    set IMAGE_NAME=!IMAGE_NAME:%%i=%%i!
)
set IMAGE_NAME=%IMAGE_NAME: =-%
set IMAGE_NAME=%IMAGE_NAME%

echo Select Registry Type:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    REM AWS ECR Configuration
    echo.
    echo AWS ECR Configuration
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO="Enter ECR Repository Name [%IMAGE_NAME%]: "
    if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
    
    REM Construct registry URL
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    
    echo.
    echo Authenticating with AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo ECR authentication failed. Please check your AWS credentials.
        exit /b 1
    )
    
    REM Check if repository exists, create if not
    echo Checking ECR repository...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Repository does not exist. Creating...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo Failed to create ECR repository.
            exit /b 1
        )
        echo Repository created successfully.
    )
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag [latest]: "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
) else if "!REGISTRY_CHOICE!"=="2" (
    REM Docker Hub Configuration
    echo.
    echo Docker Hub Configuration
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password/Token: "
    
    echo.
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub authentication failed.
        exit /b 1
    )
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag [latest]: "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/%IMAGE_NAME%:!IMAGE_TAG!
    
) else (
    echo Invalid choice. Exiting.
    exit /b 1
)

REM Build Docker image
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)

echo Docker image built successfully.

REM Push Docker image
echo.
echo Pushing Docker image to registry...
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)

echo.
echo ========================================
echo Build and Push Completed Successfully!
echo ========================================
echo Image: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Update ECS task definition with image URI: !FULL_IMAGE_NAME!
echo 2. Run deployment script: scripts\deploy-image.bat
echo.

endlocal
