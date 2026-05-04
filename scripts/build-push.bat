@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Build and Push Script for ModResorts
REM Platform: Windows
REM ============================================

echo ============================================
echo ModResorts - Docker Build and Push Script
echo ============================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts
set PROJECT_VERSION=2.0.0

REM Sanitize image name (lowercase, hyphenate special chars)
set IMAGE_NAME=%PROJECT_NAME%
for %%i in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do (
    set IMAGE_NAME=!IMAGE_NAME:%%i=%%i!
)
set IMAGE_NAME=%IMAGE_NAME: =-%
set IMAGE_NAME=%IMAGE_NAME%

echo Project: %PROJECT_NAME%
echo Version: %PROJECT_VERSION%
echo Sanitized Image Name: %IMAGE_NAME%
echo.

REM Prompt for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
echo Using tag: !IMAGE_TAG!
echo.

REM Registry selection
echo Select container registry:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    REM AWS ECR Configuration
    echo.
    echo === AWS ECR Configuration ===
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO="Enter ECR Repository Name (default: %IMAGE_NAME%): "
    if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
    
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
    echo.
    echo Full image name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with ECR
    echo Authenticating with AWS ECR...
    for /f "delims=" %%i in ('aws ecr get-login-password --region !AWS_REGION!') do set ECR_PASSWORD=%%i
    echo !ECR_PASSWORD! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR authentication failed
        exit /b 1
    )
    
    REM Check if repository exists, create if not
    echo Checking ECR repository...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
) else if "!REGISTRY_CHOICE!"=="2" (
    REM Docker Hub Configuration
    echo.
    echo === Docker Hub Configuration ===
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    set /p DOCKER_REPO="Enter repository name (default: %IMAGE_NAME%): "
    if "!DOCKER_REPO!"=="" set DOCKER_REPO=%IMAGE_NAME%
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!DOCKER_REPO!:!IMAGE_TAG!
    
    echo.
    echo Full image name: !FULL_IMAGE_NAME!
    echo.
    
    REM Authenticate with Docker Hub
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub authentication failed
        exit /b 1
    )
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

REM Build Docker image
echo.
echo Building Docker image...
echo Command: docker build -t !FULL_IMAGE_NAME! .
docker build -t !FULL_IMAGE_NAME! .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo [SUCCESS] Docker image built successfully: !FULL_IMAGE_NAME!

REM Push to registry
echo.
set /p PUSH_CONFIRM="Push image to registry? (y/n): "
if /i "!PUSH_CONFIRM!"=="y" (
    echo Pushing image to registry...
    docker push !FULL_IMAGE_NAME!
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker push failed
        exit /b 1
    )
    
    echo.
    echo ============================================
    echo [SUCCESS]
    echo ============================================
    echo Image: !FULL_IMAGE_NAME!
    echo Status: Built and pushed successfully
    echo.
    echo Next steps:
    echo 1. Update kubernetes/deployment.yaml with image URI
    echo 2. Run deploy-image.bat to deploy to AWS EKS
    echo ============================================
) else (
    echo.
    echo ============================================
    echo [SUCCESS] Build completed (not pushed)
    echo ============================================
    echo Image: !FULL_IMAGE_NAME!
    echo Status: Built locally
    echo.
    echo To push later, run:
    echo   docker push !FULL_IMAGE_NAME!
    echo ============================================
)

endlocal
