#!/bin/bash
# Complete rebuild and deployment script

set -e

echo "========================================="
echo "Complete Rebuild and Deployment"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="/Users/kumarsaikat/code/AI/work/spring-ai-alibaba-saikat"

# Step 1: Build Admin Backend
echo -e "${YELLOW}[1/5]${NC} Building Admin Backend..."
cd "$PROJECT_ROOT/spring-ai-alibaba-admin"
mvn clean package -DskipTests

if [ ! -f "spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar" ]; then
    echo "ERROR: JAR not found after build"
    exit 1
fi

# Verify ChatbotAuthController is in the JAR
if jar -tf spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar | grep -q "ChatbotAuthController"; then
    echo -e "${GREEN}✓${NC} ChatbotAuthController found in JAR"
else
    echo "ERROR: ChatbotAuthController NOT in JAR"
    exit 1
fi

# Step 2: Rebuild Docker Backend
echo -e "${YELLOW}[2/5]${NC} Rebuilding Docker Backend..."
cd "$PROJECT_ROOT/spring-ai-alibaba-admin/docker/middleware"
docker compose -f docker-compose-arm.yaml build --no-cache backend
docker compose -f docker-compose-arm.yaml up -d backend

# Step 3: Wait for Backend
echo -e "${YELLOW}[3/5]${NC} Waiting for backend to start..."
sleep 20

# Step 4: Initialize Database
echo -e "${YELLOW}[4/5]${NC} Initializing database..."
docker compose -f docker-compose-arm.yaml exec -T postgres psql -U admin -d admin < init/postgres/app-user-access.sql

# Step 5: Test Login Endpoint
echo -e "${YELLOW}[5/5]${NC} Testing login endpoint..."
RESPONSE=$(curl -s -X POST http://localhost:8080/console/v1/chatbot/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"12345"}')

if echo "$RESPONSE" | jq -e '.success == true' > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Login endpoint working!"
    echo ""
    echo "========================================="
    echo -e "${GREEN}Deployment Complete!${NC}"
    echo "========================================="
    echo ""
    echo "Test the system:"
    echo "1. Open: http://localhost:8081/"
    echo "2. Login: john@example.com / 12345"
    echo "3. Start chatting!"
else
    echo "ERROR: Login endpoint failed"
    echo "Response: $RESPONSE"
    exit 1
fi
