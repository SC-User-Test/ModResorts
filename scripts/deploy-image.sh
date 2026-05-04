#!/bin/bash

# ============================================
# Deploy to AWS EKS - ModResorts Application
# Platform: Linux/macOS
# ============================================

set -e
set -o pipefail

echo "============================================"
echo "ModResorts - AWS EKS Deployment Script"
echo "============================================"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v aws >/dev/null 2>&1 || { echo "ERROR: AWS CLI is not installed. Please install it first."; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl is not installed. Please install it first."; exit 1; }
echo "✓ Prerequisites check passed"
echo ""

# Prompt for AWS configuration
echo "=== AWS EKS Configuration ==="
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
echo ""

# Prompt for Docker image URI
echo "=== Docker Image Configuration ==="
read -p "Enter full Docker image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/modresorts:latest): " IMAGE_URI
echo ""

# Configure kubectl for EKS
echo "Configuring kubectl for EKS cluster..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for EKS cluster"
    exit 1
fi

echo "✓ kubectl configured successfully"
echo ""

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
}
echo "✓ Cluster connectivity verified"
echo ""

# Update deployment manifest with image URI
echo "Updating Kubernetes manifests..."
MANIFEST_DIR="kubernetes"

# Create backup of original files
cp $MANIFEST_DIR/deployment.yaml $MANIFEST_DIR/deployment.yaml.bak

# Replace placeholders
sed -i.tmp "s|{{IMAGE_URI}}|$IMAGE_URI|g" $MANIFEST_DIR/deployment.yaml
rm -f $MANIFEST_DIR/deployment.yaml.tmp

echo "✓ Manifests updated with image URI: $IMAGE_URI"
echo ""

# Apply Kubernetes manifests
echo "Deploying to AWS EKS..."
echo ""

echo "1. Creating namespace..."
kubectl apply -f $MANIFEST_DIR/namespace.yaml
echo ""

echo "2. Deploying application..."
kubectl apply -f $MANIFEST_DIR/deployment.yaml
echo ""

echo "3. Creating service..."
kubectl apply -f $MANIFEST_DIR/service.yaml
echo ""

echo "4. Creating ingress..."
kubectl apply -f $MANIFEST_DIR/ingress.yaml
echo ""

# Wait for deployment to complete
echo "Waiting for deployment to complete..."
kubectl rollout status deployment/modresorts -n modresorts --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n modresorts
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n modresorts -l app=modresorts --tail=50
    exit 1
fi

echo "✓ Deployment completed successfully"
echo ""

# Verify deployment
echo "Verifying deployment..."
echo ""
echo "=== Pods ==="
kubectl get pods -n modresorts
echo ""
echo "=== Services ==="
kubectl get svc -n modresorts
echo ""
echo "=== Ingress ==="
kubectl get ingress -n modresorts
echo ""

# Get ingress URL
echo "Retrieving application URL..."
INGRESS_HOST=$(kubectl get ingress modresorts-ingress -n modresorts -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

if [ -z "$INGRESS_HOST" ]; then
    echo "⚠ Ingress URL not yet available. It may take a few minutes for the load balancer to be provisioned."
    echo "Run this command to check status:"
    echo "  kubectl get ingress modresorts-ingress -n modresorts"
else
    echo "============================================"
    echo "✓ DEPLOYMENT SUCCESSFUL!"
    echo "============================================"
    echo "Application URL: http://$INGRESS_HOST/modresorts"
    echo "Health Check: http://$INGRESS_HOST/modresorts/health"
    echo ""
    echo "Note: It may take a few minutes for the load balancer to become fully operational."
fi

echo ""
echo "Useful commands:"
echo "  View pods:        kubectl get pods -n modresorts"
echo "  View logs:        kubectl logs -n modresorts -l app=modresorts"
echo "  Describe pod:     kubectl describe pod <pod-name> -n modresorts"
echo "  Scale deployment: kubectl scale deployment modresorts -n modresorts --replicas=3"
echo "  Delete deployment: kubectl delete -f kubernetes/"
echo ""
echo "============================================"

# Restore original deployment file
mv $MANIFEST_DIR/deployment.yaml.bak $MANIFEST_DIR/deployment.yaml
