# ModResorts - AWS EKS Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
4. [AWS EKS Prerequisites](#aws-eks-prerequisites)
5. [EKS Cluster Setup](#eks-cluster-setup)
6. [Kubernetes Deployment](#kubernetes-deployment)
7. [Verification and Testing](#verification-and-testing)
8. [Configuration Management](#configuration-management)
9. [Troubleshooting](#troubleshooting)
10. [Scaling and Management](#scaling-and-management)
11. [Security Considerations](#security-considerations)

---

## Prerequisites

### Required Tools
- **Docker**: Version 20.10 or higher
- **AWS CLI**: Version 2.x
- **kubectl**: Version 1.24 or higher
- **eksctl**: Version 0.140 or higher (optional, for cluster creation)
- **Java**: JDK 8 (for local development)
- **Maven**: Version 3.6 or higher (for local builds)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with permissions for:
  - ECR (Elastic Container Registry)
  - EKS (Elastic Kubernetes Service)
  - EC2, VPC, CloudFormation
  - IAM role creation

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd CompTestModresorts
```

### 2. Build the Application Locally
```bash
# Using Maven
mvn clean package

# The WAR file will be generated in target/modresorts-2.0.0.war
```

### 3. Run with Docker Compose
```bash
# Build and start the application
docker-compose up --build

# Access the application
# http://localhost:8080/modresorts

# Health check endpoint
# http://localhost:8080/modresorts/health

# Stop the application
docker-compose down
```

### 4. Test Locally
```bash
# Health check
curl http://localhost:8080/modresorts/health

# Welcome page
curl http://localhost:8080/modresorts/welcome

# Weather service
curl http://localhost:8080/modresorts/weather
```

---

## Building and Pushing Docker Images

### Option 1: Using AWS ECR

#### Linux/macOS
```bash
cd scripts
chmod +x build-push.sh
./build-push.sh
```

#### Windows
```cmd
cd scripts
build-push.bat
```

**Follow the prompts:**
1. Enter image tag (default: latest)
2. Select registry: 1 (AWS ECR)
3. Enter AWS Region (e.g., us-east-1)
4. Enter AWS Account ID
5. Enter ECR Repository Name (default: modresorts)
6. Confirm push (y/n)

**Script will:**
- Authenticate with AWS ECR
- Create ECR repository if it doesn't exist
- Build Docker image
- Push to ECR

### Option 2: Using Docker Hub

**Follow the prompts:**
1. Enter image tag (default: latest)
2. Select registry: 2 (Docker Hub)
3. Enter Docker Hub username
4. Enter Docker Hub password/token
5. Enter repository name (default: modresorts)
6. Confirm push (y/n)

### Manual Build (Alternative)
```bash
# Build image
docker build -t modresorts:latest .

# Tag for ECR
docker tag modresorts:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest

# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest
```

---

## AWS EKS Prerequisites

### 1. Install AWS CLI
```bash
# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# macOS
brew install awscli

# Windows
# Download and install from: https://aws.amazon.com/cli/
```

### 2. Configure AWS CLI
```bash
aws configure
# Enter:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (e.g., us-east-1)
# - Default output format (json)
```

### 3. Install kubectl
```bash
# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# macOS
brew install kubectl

# Windows
choco install kubernetes-cli
```

### 4. Install eksctl (Optional)
```bash
# Linux
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# macOS
brew tap weaveworks/tap
brew install weaveworks/tap/eksctl

# Windows
choco install eksctl
```

---

## EKS Cluster Setup

### Option 1: Create EKS Cluster with eksctl (Recommended)

```bash
# Create cluster with default settings
eksctl create cluster \
  --name modresorts-cluster \
  --region us-east-1 \
  --nodegroup-name modresorts-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed

# This will take 15-20 minutes
```

### Option 2: Create EKS Cluster via AWS Console

1. Navigate to EKS in AWS Console
2. Click "Create cluster"
3. Configure cluster:
   - Name: modresorts-cluster
   - Kubernetes version: 1.27 or higher
   - Cluster service role: Create new or select existing
4. Configure networking:
   - VPC: Select or create new
   - Subnets: Select at least 2 in different AZs
   - Security groups: Default or custom
5. Configure logging (optional)
6. Review and create
7. Create node group:
   - Name: modresorts-nodes
   - Instance type: t3.medium
   - Desired size: 2
   - Min size: 1
   - Max size: 4

### Verify Cluster
```bash
# Update kubeconfig
aws eks update-kubeconfig --region us-east-1 --name modresorts-cluster

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### Install AWS Load Balancer Controller

The AWS Load Balancer Controller is required for Ingress to work:

```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.5.4/docs/install/iam_policy.json

aws iam create-policy \
    --policy-name AWSLoadBalancerControllerIAMPolicy \
    --policy-document file://iam_policy.json

# Create IAM role and service account
eksctl create iamserviceaccount \
  --cluster=modresorts-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name AmazonEKSLoadBalancerControllerRole \
  --attach-policy-arn=arn:aws:iam::<AWS_ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Install the controller using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=modresorts-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller

# Verify installation
kubectl get deployment -n kube-system aws-load-balancer-controller
```

---

## Kubernetes Deployment

### Automated Deployment

#### Linux/macOS
```bash
cd scripts
chmod +x deploy-image.sh
./deploy-image.sh
```

#### Windows
```cmd
cd scripts
deploy-image.bat
```

**Follow the prompts:**
1. Enter AWS Region (e.g., us-east-1)
2. Enter EKS Cluster Name (e.g., modresorts-cluster)
3. Enter full Docker image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest)

**Script will:**
- Configure kubectl for your EKS cluster
- Verify cluster connectivity
- Update Kubernetes manifests with your image URI
- Apply all manifests (namespace, deployment, service, ingress)
- Wait for deployment to complete
- Display deployment status and application URL

### Manual Deployment

```bash
# 1. Update deployment.yaml with your image URI
# Edit kubernetes/deployment.yaml and replace {{IMAGE_URI}} with your actual image

# 2. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 3. Wait for deployment
kubectl rollout status deployment/modresorts -n modresorts

# 4. Verify deployment
kubectl get all -n modresorts
```

---

## Verification and Testing

### Check Deployment Status
```bash
# View all resources
kubectl get all -n modresorts

# View pods
kubectl get pods -n modresorts

# View services
kubectl get svc -n modresorts

# View ingress
kubectl get ingress -n modresorts
```

### Check Pod Logs
```bash
# View logs for all pods
kubectl logs -n modresorts -l app=modresorts

# View logs for specific pod
kubectl logs -n modresorts <pod-name>

# Follow logs
kubectl logs -n modresorts -l app=modresorts -f

# View previous logs (if pod restarted)
kubectl logs -n modresorts <pod-name> --previous
```

### Describe Resources
```bash
# Describe deployment
kubectl describe deployment modresorts -n modresorts

# Describe pod
kubectl describe pod <pod-name> -n modresorts

# Describe service
kubectl describe svc modresorts-service -n modresorts

# Describe ingress
kubectl describe ingress modresorts-ingress -n modresorts
```

### Test Application

```bash
# Get ingress URL
INGRESS_URL=$(kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# Test health endpoint
curl http://$INGRESS_URL/modresorts/health

# Test welcome page
curl http://$INGRESS_URL/modresorts/welcome

# Test weather service
curl http://$INGRESS_URL/modresorts/weather
```

### Access Application in Browser
```
http://<ingress-hostname>/modresorts
```

---

## Configuration Management

### Environment Variables

Edit `kubernetes/deployment.yaml` to add environment variables:

```yaml
env:
- name: DATABASE_URL
  value: "jdbc:postgresql://your-db-host:5432/modresorts"
- name: DATABASE_USERNAME
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: db-username
- name: DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: modresorts-secrets
      key: db-password
```

### Secrets Management

Create Kubernetes secrets for sensitive data:

```bash
# Create secret from literals
kubectl create secret generic modresorts-secrets \
  --from-literal=db-username=admin \
  --from-literal=db-password=secretpassword \
  --from-literal=api-key=your-api-key \
  -n modresorts

# Create secret from file
kubectl create secret generic modresorts-config \
  --from-file=application.properties \
  -n modresorts

# View secrets
kubectl get secrets -n modresorts

# Describe secret (values are base64 encoded)
kubectl describe secret modresorts-secrets -n modresorts
```

### ConfigMaps

Create ConfigMaps for non-sensitive configuration:

```bash
# Create ConfigMap from file
kubectl create configmap modresorts-config \
  --from-file=application.properties \
  -n modresorts

# Create ConfigMap from literals
kubectl create configmap modresorts-config \
  --from-literal=log.level=INFO \
  --from-literal=app.timezone=UTC \
  -n modresorts

# Use ConfigMap in deployment
# Add to deployment.yaml:
envFrom:
- configMapRef:
    name: modresorts-config
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

**Check pod status:**
```bash
kubectl get pods -n modresorts
kubectl describe pod <pod-name> -n modresorts
```

**Common causes:**
- Image pull errors (check image URI and ECR permissions)
- Resource constraints (check node capacity)
- Configuration errors (check environment variables)

**Solutions:**
```bash
# Check events
kubectl get events -n modresorts --sort-by='.lastTimestamp'

# Check node resources
kubectl top nodes

# Check pod resources
kubectl top pods -n modresorts
```

#### 2. Image Pull Errors

**Error:** `ErrImagePull` or `ImagePullBackOff`

**Solutions:**
```bash
# Verify image exists in ECR
aws ecr describe-images --repository-name modresorts --region us-east-1

# Check ECR permissions
aws ecr get-login-password --region us-east-1

# Create image pull secret (if using private registry)
kubectl create secret docker-registry ecr-secret \
  --docker-server=123456789.dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region us-east-1) \
  -n modresorts

# Add to deployment.yaml:
imagePullSecrets:
- name: ecr-secret
```

#### 3. Service Not Accessible

**Check service:**
```bash
kubectl get svc -n modresorts
kubectl describe svc modresorts-service -n modresorts
```

**Test service internally:**
```bash
# Port forward to test
kubectl port-forward -n modresorts svc/modresorts-service 8080:80

# Test locally
curl http://localhost:8080/modresorts/health
```

#### 4. Ingress Not Working

**Check ingress:**
```bash
kubectl get ingress -n modresorts
kubectl describe ingress modresorts-ingress -n modresorts
```

**Common causes:**
- AWS Load Balancer Controller not installed
- Security group rules blocking traffic
- Subnet configuration issues

**Solutions:**
```bash
# Verify Load Balancer Controller
kubectl get deployment -n kube-system aws-load-balancer-controller

# Check ALB in AWS Console
aws elbv2 describe-load-balancers --region us-east-1

# Check security groups
aws ec2 describe-security-groups --region us-east-1
```

#### 5. Application Crashes or Restarts

**Check logs:**
```bash
kubectl logs -n modresorts <pod-name> --previous
```

**Common causes:**
- Out of memory (increase memory limits)
- Application errors (check logs)
- Health check failures (adjust probe settings)

**Solutions:**
```bash
# Increase resources in deployment.yaml
resources:
  limits:
    memory: "2Gi"
    cpu: "1000m"

# Adjust health check probes
livenessProbe:
  initialDelaySeconds: 120
  periodSeconds: 30
  timeoutSeconds: 10
```

#### 6. Database Connection Issues

**Check connectivity:**
```bash
# Exec into pod
kubectl exec -it -n modresorts <pod-name> -- /bin/bash

# Test database connection (if tools available)
# Or check application logs for connection errors
```

**Solutions:**
- Verify database endpoint is accessible from EKS
- Check security groups allow traffic from EKS nodes
- Verify credentials in secrets
- Check VPC peering or VPN configuration

---

## Scaling and Management

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment modresorts -n modresorts --replicas=3

# Verify scaling
kubectl get pods -n modresorts
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA
kubectl autoscale deployment modresorts \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n modresorts

# View HPA status
kubectl get hpa -n modresorts

# Describe HPA
kubectl describe hpa modresorts -n modresorts
```

**HPA YAML (alternative):**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: modresorts-hpa
  namespace: modresorts
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: modresorts
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/modresorts modresorts=<new-image-uri> -n modresorts

# Check rollout status
kubectl rollout status deployment/modresorts -n modresorts

# View rollout history
kubectl rollout history deployment/modresorts -n modresorts

# Rollback to previous version
kubectl rollout undo deployment/modresorts -n modresorts

# Rollback to specific revision
kubectl rollout undo deployment/modresorts --to-revision=2 -n modresorts
```

### Resource Monitoring

```bash
# Install metrics server (if not already installed)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# View resource usage
kubectl top nodes
kubectl top pods -n modresorts

# View resource limits
kubectl describe deployment modresorts -n modresorts | grep -A 5 Limits
```

---

## Security Considerations

### 1. Image Security

- **Use specific image tags** (not `latest`) in production
- **Scan images for vulnerabilities** using AWS ECR image scanning
- **Use minimal base images** (consider distroless or alpine)
- **Keep base images updated** regularly

```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name modresorts \
  --image-scanning-configuration scanOnPush=true \
  --region us-east-1

# View scan results
aws ecr describe-image-scan-findings \
  --repository-name modresorts \
  --image-id imageTag=latest \
  --region us-east-1
```

### 2. Network Security

- **Use Network Policies** to restrict pod-to-pod communication
- **Configure Security Groups** properly for EKS nodes
- **Use private subnets** for worker nodes
- **Enable VPC Flow Logs** for network monitoring

```yaml
# Example Network Policy
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: modresorts-netpol
  namespace: modresorts
spec:
  podSelector:
    matchLabels:
      app: modresorts
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: modresorts
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443
```

### 3. RBAC (Role-Based Access Control)

```bash
# Create service account
kubectl create serviceaccount modresorts-sa -n modresorts

# Create role
kubectl create role modresorts-role \
  --verb=get,list,watch \
  --resource=pods,services \
  -n modresorts

# Create role binding
kubectl create rolebinding modresorts-rolebinding \
  --role=modresorts-role \
  --serviceaccount=modresorts:modresorts-sa \
  -n modresorts

# Use service account in deployment
# Add to deployment.yaml:
serviceAccountName: modresorts-sa
```

### 4. Secrets Management

- **Never commit secrets to Git**
- **Use AWS Secrets Manager** or **AWS Systems Manager Parameter Store**
- **Rotate secrets regularly**
- **Use IAM roles for service accounts** (IRSA) for AWS service access

```bash
# Install External Secrets Operator (optional)
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace
```

### 5. Pod Security

```yaml
# Add security context to deployment.yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: true
```

### 6. TLS/SSL

```yaml
# Update ingress.yaml for HTTPS
metadata:
  annotations:
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
```

---

## Java-Specific Considerations

### JVM Tuning for Containers

The Dockerfile includes optimized JVM settings:
```
-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

**Adjust based on your needs:**
- Increase heap size for memory-intensive applications
- Use G1GC for better garbage collection: `-XX:+UseG1GC`
- Enable GC logging: `-Xlog:gc*:file=/var/log/gc.log`

### Tomcat Configuration

The application runs on Tomcat 9. To customize:

1. Create custom `server.xml`
2. Add as ConfigMap
3. Mount in deployment

```bash
kubectl create configmap tomcat-config \
  --from-file=server.xml \
  -n modresorts
```

### Application Monitoring

Consider adding:
- **Spring Boot Actuator** for metrics
- **Prometheus** for monitoring
- **Grafana** for visualization
- **ELK Stack** for log aggregation

---

## Additional Resources

- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Java Container Best Practices](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Runtime.html)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update base images** monthly
2. **Review and rotate secrets** quarterly
3. **Update Kubernetes version** as needed
4. **Monitor resource usage** and adjust limits
5. **Review logs** for errors and warnings
6. **Test disaster recovery** procedures

### Backup and Disaster Recovery

```bash
# Backup Kubernetes resources
kubectl get all -n modresorts -o yaml > modresorts-backup.yaml

# Backup using Velero (recommended)
velero backup create modresorts-backup --include-namespaces modresorts
```

---

## Conclusion

This guide provides comprehensive instructions for deploying the ModResorts application to AWS EKS. Follow the steps carefully, and refer to the troubleshooting section if you encounter issues.

For production deployments, ensure you:
- Use specific image tags
- Enable monitoring and logging
- Implement proper security measures
- Set up automated backups
- Configure autoscaling
- Test disaster recovery procedures

**Happy Deploying! 🚀**
