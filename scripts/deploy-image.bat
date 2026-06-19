@echo off
setlocal enabledelayedexpansion

REM ECS Fargate Deployment Script for ModResorts Application (Windows)
REM Deploys containerized application to AWS ECS Fargate

echo ==========================================
echo ModResorts - ECS Fargate Deployment
echo ==========================================
echo.

REM Prompt for deployment configuration
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter ECS Cluster Name: "
set /p VPC_ID="Enter VPC ID: "
set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, at least 2): "
set /p SECURITY_GROUP="Enter Security Group ID: "
set /p IMAGE_URI="Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): "

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)

REM Trim whitespace
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

echo.
echo Configuration Summary:
echo   Region: !AWS_REGION!
echo   Cluster: !CLUSTER_NAME!
echo   VPC: !VPC_ID!
echo   Subnets: !SUBNET_1!, !SUBNET_2!
echo   Security Group: !SECURITY_GROUP!
echo   Image: !IMAGE_URI!
echo.

REM Get AWS Account ID
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!
echo.

REM Check if ECS cluster exists
echo Checking ECS cluster...
aws ecs describe-clusters --clusters !CLUSTER_NAME! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Cluster does not exist. Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name !CLUSTER_NAME! --region !AWS_REGION!
    echo Cluster created successfully
)
echo.

REM Create CloudWatch log group
echo Creating CloudWatch log group...
aws logs create-log-group --log-group-name "/ecs/modresorts" --region !AWS_REGION! 2>nul
if !ERRORLEVEL! neq 0 (
    echo Log group already exists
)
echo.

REM Ask about load balancer
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo.
    echo Creating Application Load Balancer and Target Group...
    
    set ALB_NAME=modresorts-alb
    echo Creating ALB: !ALB_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name !ALB_NAME! --subnets !SUBNET_1! !SUBNET_2! --security-groups !SECURITY_GROUP! --scheme internet-facing --type application --ip-address-type ipv4 --region !AWS_REGION! --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    
    echo ALB created: !ALB_ARN!
    
    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns !ALB_ARN! --region !AWS_REGION! --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    set TG_NAME=modresorts-tg
    echo Creating Target Group: !TG_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name !TG_NAME! --protocol HTTP --port 8080 --vpc-id !VPC_ID! --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region !AWS_REGION! --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    
    echo Target Group created: !TARGET_GROUP_ARN!
    
    echo Creating ALB listener...
    aws elbv2 create-listener --load-balancer-arn !ALB_ARN! --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn=!TARGET_GROUP_ARN! --region !AWS_REGION! >nul
    
    echo Listener created successfully
    echo.
    
    REM Update service definition with target group ARN
    powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{TARGET_GROUP_ARN}}', '!TARGET_GROUP_ARN!' | Set-Content ecs\service-definition.json"
) else (
    echo.
    echo Skipping load balancer creation...
    
    REM Remove loadBalancers section from service definition
    powershell -Command "$json = Get-Content ecs\service-definition.json | ConvertFrom-Json; $json.PSObject.Properties.Remove('loadBalancers'); $json.PSObject.Properties.Remove('healthCheckGracePeriodSeconds'); $json | ConvertTo-Json -Depth 10 | Set-Content ecs\service-definition.json"
)

REM Replace placeholders in task definition
echo Preparing task definition...
powershell -Command "(Get-Content ecs\task-definition.json) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content ecs\task-definition.json"
powershell -Command "(Get-Content ecs\task-definition.json) -replace '{{AWS_REGION}}', '!AWS_REGION!' | Set-Content ecs\task-definition.json"
powershell -Command "(Get-Content ecs\task-definition.json) -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' | Set-Content ecs\task-definition.json"

REM Replace placeholders in service definition
powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' | Set-Content ecs\service-definition.json"
powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{SUBNET_1}}', '!SUBNET_1!' | Set-Content ecs\service-definition.json"
powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{SUBNET_2}}', '!SUBNET_2!' | Set-Content ecs\service-definition.json"
powershell -Command "(Get-Content ecs\service-definition.json) -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' | Set-Content ecs\service-definition.json"

echo.
echo Registering ECS task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json --region !AWS_REGION! --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

echo Task definition registered: !TASK_DEF_ARN!
echo.

REM Check if service exists
echo Checking if service exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services modresorts-service --region !AWS_REGION! --query "services[0].serviceName" --output text 2^>nul') do set SERVICE_EXISTS=%%i

if "!SERVICE_EXISTS!"=="None" (
    echo Service does not exist. Creating new service...
    aws ecs create-service --cli-input-json file://ecs/service-definition.json --region !AWS_REGION! >nul
    echo Service created successfully
) else (
    echo Service exists. Updating service...
    aws ecs update-service --cluster !CLUSTER_NAME! --service modresorts-service --task-definition !TASK_DEF_ARN! --desired-count 2 --region !AWS_REGION! >nul
    echo Service updated successfully
)

echo.
echo Waiting for service to become stable...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services modresorts-service --region !AWS_REGION!

echo.
echo ==========================================
echo Deployment Complete!
echo ==========================================
echo.

echo Service Details:
aws ecs describe-services --cluster !CLUSTER_NAME! --services modresorts-service --region !AWS_REGION! --query "services[0].[serviceName,status,runningCount,desiredCount]" --output table

echo.
echo CloudWatch Logs:
echo   Log Group: /ecs/modresorts
echo   Region: !AWS_REGION!
echo.

if /i "!NEED_LB!"=="y" (
    echo Application Load Balancer:
    echo   DNS Name: !ALB_DNS!
    echo   Access your application at: http://!ALB_DNS!
    echo.
)

echo To view logs:
echo   aws logs tail /ecs/modresorts --follow --region !AWS_REGION!
echo.
echo To check service status:
echo   aws ecs describe-services --cluster !CLUSTER_NAME! --services modresorts-service --region !AWS_REGION!
echo.
echo ==========================================

endlocal
