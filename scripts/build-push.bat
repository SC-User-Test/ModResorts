@echo off
setlocal enabledelayedexpansion

REM Build and Push Script for ModResorts Application (Windows)
REM Supports AWS ECR and Docker Hub registries

echo ==========================================
echo ModResorts - Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts
set IMAGE_TAG_INPUT=

REM Prompt for image tag
set /p IMAGE_TAG_INPUT="Enter image tag (default: latest): "
if "!IMAGE_TAG_INPUT!"=="" (
    set IMAGE_TAG=latest
) else (
    set IMAGE_TAG=!IMAGE_TAG_INPUT!
)

REM Sanitize image tag using PowerShell
for /f "delims=" %%i in ('powershell -Command "$tag='!IMAGE_TAG!'; $tag=$tag.ToLower() -replace '[^a-z0-9.-]','-'; $tag=$tag -replace '^-+|-+$',''; if([string]::IsNullOrEmpty($tag)){'latest'}else{$tag}"') do set IMAGE_TAG=%%i

echo.
echo Using image tag: !IMAGE_TAG!
echo.

REM Registry selection
echo Select container registry:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo.
    echo === AWS ECR Configuration ===
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO_INPUT="Enter ECR Repository Name (default: modresorts): "
    
    if "!ECR_REPO_INPUT!"=="" (
        set ECR_REPO=modresorts
    ) else (
        set ECR_REPO=!ECR_REPO_INPUT!
    )
    
    REM Sanitize ECR repository name
    for /f "delims=" %%i in ('powershell -Command "$repo='!ECR_REPO!'; $repo=$repo.ToLower() -replace '[^a-z0-9/_-]','-'; $repo=$repo -replace '^-+|-+$',''; $repo"') do set ECR_REPO=%%i
    
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
    echo.
    echo Authenticating with AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR authentication failed
        exit /b 1
    )
    
    echo Checking if ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Repository does not exist. Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo.
    echo === Docker Hub Configuration ===
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
    
    REM Sanitize image name
    for /f "delims=" %%i in ('powershell -Command "$name='!PROJECT_NAME!'; $name=$name.ToLower() -replace '[^a-z0-9]','-'; $name=$name -replace '^-+|-+$',''; $name"') do set IMAGE_NAME=%%i
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
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

echo.
echo ==========================================
echo Building Docker image...
echo Image: !FULL_IMAGE_NAME!
echo ==========================================
echo.

REM Build Docker image
docker build -t !FULL_IMAGE_NAME! .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo ==========================================
echo Pushing image to registry...
echo ==========================================
echo.

REM Push Docker image
docker push !FULL_IMAGE_NAME!

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Image successfully built and pushed:
echo   !FULL_IMAGE_NAME!
echo.
echo To deploy to ECS, use the deploy-image.bat script
echo ==========================================

endlocal
