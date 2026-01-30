#!/bin/bash
set -e

echo "=== Rebuilding Spring AI Alibaba Backend for Docker ==="

# Navigate to admin directory
cd "$(dirname "$0")/../.."
echo "Working directory: $(pwd)"

# Step 1: Rebuild the JAR
echo "Step 1: Building JAR..."
mvn clean package -DskipTests -pl spring-ai-alibaba-admin-server-start -am

# Step 2: Build Docker image
echo "Step 2: Building Docker image..."
docker build -f docker/middleware/Dockerfile.backend -t spring-ai-alibaba-admin-server:latest .

# Step 3: Restart backend container
echo "Step 3: Restarting backend container..."
cd docker/middleware
docker compose -f docker-compose-arm.yaml up -d --force-recreate backend

echo "=== Build complete! ==="
echo "Waiting for backend to start..."
sleep 30

# Check status
docker ps --filter "name=saa-backend"
echo ""
echo "Check logs with: docker logs saa-backend"
