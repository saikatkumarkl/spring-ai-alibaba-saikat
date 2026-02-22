#!/bin/bash
set -e

echo "=== Rebuilding CordonData Backend for Docker ==="

# Navigate to admin directory
cd "$(dirname "$0")/../.."
echo "Working directory: $(pwd)"

# Step 1: Rebuild the JAR
echo "Step 1: Building JAR..."
mvn clean package -DskipTests -pl spring-ai-alibaba-admin-server-start -am

# Step 2: Build Docker image
echo "Step 2: Building Docker image..."
docker build -f docker/middleware/Dockerfile.backend -t cordondata-admin-server:latest .

# Step 3: Restart backend container
echo "Step 3: Restarting backend container..."
cd docker/middleware
docker compose -f docker-compose-arm.yaml up -d --force-recreate backend

echo "=== Build complete! ==="
echo "Waiting for backend to start..."
sleep 30

# Check status
docker ps --filter "name=cordondata-backend"
echo ""
echo "Check logs with: docker logs cordondata-backend"
