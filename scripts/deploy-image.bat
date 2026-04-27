@echo off
setlocal enabledelayedexpansion

REM ECS Fargate Deployment Script for ModResorts Application (Windows)
REM This script deploys the containerized application to AWS ECS Fargate

echo ============================================
echo ModResorts - ECS Fargate Deployment Script
echo ============================================
echo.

REM Project configuration
set PROJECT_NAME=modresorts
set SERVICE_NAME=modresorts-service
set TASK_FAMILY=modresorts-task

REM Prompt for AWS configuration
echo === AWS Configuration ===
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set AWS_DEFAULT_REGION=!AWS_REGION!

set /p CLUSTER_NAME="Enter ECS Cluster Name: "

REM Check if cluster exists, create if it doesn't
echo Checking if ECS cluster exists...
aws ecs describe-clusters --clusters !CLUSTER_NAME! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Cluster does not exist. Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name !CLUSTER_NAME! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECS cluster
        exit /b 1
    )
    echo ECS cluster created successfully
)

echo.
echo === Network Configuration ===
set /p VPC_ID="Enter VPC ID: "
set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, at least 2): "
set /p SECURITY_GROUP="Enter Security Group ID: "

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

echo.
echo === Container Image Configuration ===
set /p IMAGE_URI="Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "

REM Get AWS Account ID
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!

echo.
echo === Load Balancer Configuration ===
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo Creating Application Load Balancer and Target Group...
    
    REM Create ALB
    set ALB_NAME=!PROJECT_NAME!-alb
    echo Creating Application Load Balancer: !ALB_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name !ALB_NAME! --subnets !SUBNET_1! !SUBNET_2! --security-groups !SECURITY_GROUP! --scheme internet-facing --type application --ip-address-type ipv4 --region !AWS_REGION! --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    
    echo ALB created: !ALB_ARN!
    
    REM Get ALB DNS name
    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns !ALB_ARN! --region !AWS_REGION! --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    REM Create Target Group with target-type ip
    set TG_NAME=!PROJECT_NAME!-tg
    echo Creating Target Group: !TG_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name !TG_NAME! --protocol HTTP --port 8080 --vpc-id !VPC_ID! --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region !AWS_REGION! --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    
    echo Target Group created: !TARGET_GROUP_ARN!
    
    REM Create Listener
    echo Creating ALB Listener...
    aws elbv2 create-listener --load-balancer-arn !ALB_ARN! --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn=!TARGET_GROUP_ARN! --region !AWS_REGION! >nul
    
    echo ALB Listener created
    
    REM Copy service definition
    copy ecs\service-definition.json %TEMP%\service-definition-temp.json >nul
    set TEMP_SERVICE_DEF=%TEMP%\service-definition-temp.json
) else (
    REM Remove load balancer section from service definition
    echo Removing load balancer configuration from service definition...
    powershell -Command "(Get-Content ecs\service-definition.json | ConvertFrom-Json | Select-Object * -ExcludeProperty loadBalancers,healthCheckGracePeriodSeconds | ConvertTo-Json -Depth 10) | Set-Content %TEMP%\service-definition-temp.json"
    set TEMP_SERVICE_DEF=%TEMP%\service-definition-temp.json
)

echo.
echo === Preparing Deployment Manifests ===

REM Create temporary files with replaced placeholders
set TEMP_TASK_DEF=%TEMP%\task-definition-temp.json

REM Replace placeholders in task definition
powershell -Command "(Get-Content ecs\task-definition.json) -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{AWS_REGION}}','!AWS_REGION!' -replace '{{ACCOUNT_ID}}','!ACCOUNT_ID!' | Set-Content !TEMP_TASK_DEF!"

REM Replace placeholders in service definition
if /i "!NEED_LB!"=="y" (
    powershell -Command "(Get-Content !TEMP_SERVICE_DEF!) -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' -replace '{{TARGET_GROUP_ARN}}','!TARGET_GROUP_ARN!' | Set-Content !TEMP_SERVICE_DEF!"
) else (
    powershell -Command "(Get-Content !TEMP_SERVICE_DEF!) -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' | Set-Content !TEMP_SERVICE_DEF!"
)

echo Deployment manifests prepared

REM Create CloudWatch Log Group
echo.
echo Creating CloudWatch Log Group...
aws logs create-log-group --log-group-name "/ecs/!PROJECT_NAME!" --region !AWS_REGION! 2>nul || echo Log group already exists

REM Register task definition
echo.
echo === Registering ECS Task Definition ===
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json file://!TEMP_TASK_DEF! --region !AWS_REGION! --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

echo Task definition registered: !TASK_DEF_ARN!

REM Check if service exists
echo.
echo Checking if ECS service exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[0].serviceName" --output text 2^>nul') do set SERVICE_EXISTS=%%i

if "!SERVICE_EXISTS!"=="!SERVICE_NAME!" (
    REM Update existing service
    echo Service exists. Updating service...
    aws ecs update-service --cluster !CLUSTER_NAME! --service !SERVICE_NAME! --task-definition !TASK_DEF_ARN! --region !AWS_REGION! --force-new-deployment >nul
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to update service
        exit /b 1
    )
    
    echo Service updated successfully
) else (
    REM Create new service
    echo Service does not exist. Creating new service...
    aws ecs create-service --cli-input-json file://!TEMP_SERVICE_DEF! --region !AWS_REGION! >nul
    
    if !ERRORLEVEL! neq 0 (
        echo Failed to create service
        exit /b 1
    )
    
    echo Service created successfully
)

REM Wait for service to become stable
echo.
echo Waiting for service to become stable (this may take a few minutes)...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!

echo Service is stable

REM Verify deployment
echo.
echo === Deployment Verification ===
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[0].[serviceName,status,runningCount,desiredCount]" --output table

echo.
echo ============================================
echo Deployment Completed Successfully!
echo ============================================
echo.
echo Service Details:
echo   Cluster: !CLUSTER_NAME!
echo   Service: !SERVICE_NAME!
echo   Task Definition: !TASK_DEF_ARN!
echo   Region: !AWS_REGION!
echo.

if /i "!NEED_LB!"=="y" (
    echo Load Balancer:
    echo   DNS Name: http://!ALB_DNS!
    echo   Access your application at: http://!ALB_DNS!
    echo.
)

echo CloudWatch Logs:
echo   Log Group: /ecs/!PROJECT_NAME!
echo   View logs: aws logs tail /ecs/!PROJECT_NAME! --follow --region !AWS_REGION!
echo.
echo Useful Commands:
echo   List tasks: aws ecs list-tasks --cluster !CLUSTER_NAME! --service-name !SERVICE_NAME! --region !AWS_REGION!
echo   Describe service: aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
echo   View logs: aws logs tail /ecs/!PROJECT_NAME! --follow --region !AWS_REGION!
echo.

REM Cleanup temporary files
del /q !TEMP_TASK_DEF! !TEMP_SERVICE_DEF! 2>nul

endlocal
