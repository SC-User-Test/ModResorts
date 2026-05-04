@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Deploy to AWS EKS - ModResorts Application
REM Platform: Windows
REM ============================================

echo ============================================
echo ModResorts - AWS EKS Deployment Script
echo ============================================
echo.

REM Check prerequisites
echo Checking prerequisites...
where aws >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR: AWS CLI is not installed. Please install it first.
    exit /b 1
)
where kubectl >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR: kubectl is not installed. Please install it first.
    exit /b 1
)
echo [SUCCESS] Prerequisites check passed
echo.

REM Prompt for AWS configuration
echo === AWS EKS Configuration ===
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
echo.

REM Prompt for Docker image URI
echo === Docker Image Configuration ===
set /p IMAGE_URI="Enter full Docker image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "
echo.

REM Configure kubectl for EKS
echo Configuring kubectl for EKS cluster...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo [SUCCESS] kubectl configured successfully
echo.

REM Verify cluster connectivity
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster
    exit /b 1
)
echo [SUCCESS] Cluster connectivity verified
echo.

REM Update deployment manifest with image URI
echo Updating Kubernetes manifests...
set MANIFEST_DIR=kubernetes

REM Create backup of original files
copy %MANIFEST_DIR%\deployment.yaml %MANIFEST_DIR%\deployment.yaml.bak >nul

REM Replace placeholders using PowerShell
powershell -Command "(Get-Content %MANIFEST_DIR%\deployment.yaml) -replace '{{IMAGE_URI}}', '%IMAGE_URI%' | Set-Content %MANIFEST_DIR%\deployment.yaml"

echo [SUCCESS] Manifests updated with image URI: !IMAGE_URI!
echo.

REM Apply Kubernetes manifests
echo Deploying to AWS EKS...
echo.

echo 1. Creating namespace...
kubectl apply -f %MANIFEST_DIR%\namespace.yaml
echo.

echo 2. Deploying application...
kubectl apply -f %MANIFEST_DIR%\deployment.yaml
echo.

echo 3. Creating service...
kubectl apply -f %MANIFEST_DIR%\service.yaml
echo.

echo 4. Creating ingress...
kubectl apply -f %MANIFEST_DIR%\ingress.yaml
echo.

REM Wait for deployment to complete
echo Waiting for deployment to complete...
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo.
    echo Checking pod status...
    kubectl get pods -n modresorts
    echo.
    echo Checking pod logs...
    kubectl logs -n modresorts -l app=modresorts --tail=50
    exit /b 1
)

echo [SUCCESS] Deployment completed successfully
echo.

REM Verify deployment
echo Verifying deployment...
echo.
echo === Pods ===
kubectl get pods -n modresorts
echo.
echo === Services ===
kubectl get svc -n modresorts
echo.
echo === Ingress ===
kubectl get ingress -n modresorts
echo.

REM Get ingress URL
echo Retrieving application URL...
for /f "delims=" %%i in ('kubectl get ingress modresorts-ingress -n modresorts -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}"') do set INGRESS_HOST=%%i

if "!INGRESS_HOST!"=="" (
    echo [WARNING] Ingress URL not yet available. It may take a few minutes for the load balancer to be provisioned.
    echo Run this command to check status:
    echo   kubectl get ingress modresorts-ingress -n modresorts
) else (
    echo ============================================
    echo [SUCCESS] DEPLOYMENT SUCCESSFUL!
    echo ============================================
    echo Application URL: http://!INGRESS_HOST!/modresorts
    echo Health Check: http://!INGRESS_HOST!/modresorts/health
    echo.
    echo Note: It may take a few minutes for the load balancer to become fully operational.
)

echo.
echo Useful commands:
echo   View pods:        kubectl get pods -n modresorts
echo   View logs:        kubectl logs -n modresorts -l app=modresorts
echo   Describe pod:     kubectl describe pod ^<pod-name^> -n modresorts
echo   Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3
echo   Delete deployment: kubectl delete -f kubernetes/
echo.
echo ============================================

REM Restore original deployment file
copy %MANIFEST_DIR%\deployment.yaml.bak %MANIFEST_DIR%\deployment.yaml >nul
del %MANIFEST_DIR%\deployment.yaml.bak >nul

endlocal
