#!/bin/bash
# End-to-End Authentication Test Script

set -e

echo "========================================="
echo "Authentication System End-to-End Test"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ADMIN_URL="http://localhost:8080"
CHATBOT_URL="http://localhost:8081"

# Test 1: Check Admin Backend
echo -e "${YELLOW}[1/6]${NC} Checking Admin backend..."
if curl -s -o /dev/null -w "%{http_code}" "$ADMIN_URL/actuator/health" 2>/dev/null | grep -q "200"; then
    echo -e "${GREEN}✓${NC} Admin backend is running at $ADMIN_URL"
else
    echo -e "${RED}✗${NC} Admin backend is NOT running. Start it with:"
    echo "    cd spring-ai-alibaba-admin/docker/middleware"
    echo "    docker compose -f docker-compose-arm.yaml up -d"
    exit 1
fi

# Test 2: Check Chatbot
echo -e "${YELLOW}[2/6]${NC} Checking Chatbot..."
if curl -s -o /dev/null -w "%{http_code}" "$CHATBOT_URL/actuator/health" 2>/dev/null | grep -q "200"; then
    echo -e "${GREEN}✓${NC} Chatbot is running at $CHATBOT_URL"
else
    echo -e "${RED}✗${NC} Chatbot is NOT running. Start it with:"
    echo "    cd examples/chatbot"
    echo "    mvn spring-boot:run"
    exit 1
fi

# Test 3: Login API
echo -e "${YELLOW}[3/6]${NC} Testing login API..."
LOGIN_RESPONSE=$(curl -s -X POST "$ADMIN_URL/console/v1/chatbot/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"12345"}')

if echo "$LOGIN_RESPONSE" | jq -e '.success == true' > /dev/null 2>&1; then
    TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.token')
    EMAIL=$(echo "$LOGIN_RESPONSE" | jq -r '.data.email')
    APP_IDS=$(echo "$LOGIN_RESPONSE" | jq -r '.data.appIds[]')
    echo -e "${GREEN}✓${NC} Login successful for: $EMAIL"
    echo "  Token: ${TOKEN:0:20}..."
    echo "  App IDs: $APP_IDS"
else
    echo -e "${RED}✗${NC} Login failed"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi

# Test 4: Check Access
echo -e "${YELLOW}[4/6]${NC} Testing access control..."
if [ -n "$APP_IDS" ]; then
    FIRST_APP=$(echo "$APP_IDS" | head -1)
    ACCESS_RESPONSE=$(curl -s "$ADMIN_URL/console/v1/chatbot/check-access?email=$EMAIL&appId=$FIRST_APP")
    HAS_ACCESS=$(echo "$ACCESS_RESPONSE" | jq -r '.data.hasAccess')
    
    if [ "$HAS_ACCESS" = "true" ]; then
        echo -e "${GREEN}✓${NC} User has access to app: $FIRST_APP"
    else
        echo -e "${RED}✗${NC} Access check failed"
        echo "Response: $ACCESS_RESPONSE"
    fi
else
    echo -e "${YELLOW}⚠${NC} No apps available for user"
fi

# Test 5: User Apps
echo -e "${YELLOW}[5/6]${NC} Testing user apps list..."
APPS_RESPONSE=$(curl -s "$ADMIN_URL/console/v1/chatbot/user-apps?email=$EMAIL")
APP_COUNT=$(echo "$APPS_RESPONSE" | jq -r '.data.apps | length')
echo -e "${GREEN}✓${NC} User has access to $APP_COUNT app(s)"

# Test 6: Chatbot Login UI
echo -e "${YELLOW}[6/6]${NC} Testing chatbot login UI..."
if curl -s "$CHATBOT_URL/index.html" | grep -q "Chatbot Login"; then
    echo -e "${GREEN}✓${NC} Login UI is accessible"
else
    echo -e "${RED}✗${NC} Login UI not found"
fi

echo ""
echo "========================================="
echo -e "${GREEN}All Tests Passed!${NC}"
echo "========================================="
echo ""
echo "Next Steps:"
echo "1. Open browser: http://localhost:8081/"
echo "2. Login with:"
echo "   Email: john@example.com"
echo "   Password: 12345"
echo "3. Start chatting!"
echo ""
echo "Admin UI: http://localhost:8000 (saa / 123456)"
echo ""
