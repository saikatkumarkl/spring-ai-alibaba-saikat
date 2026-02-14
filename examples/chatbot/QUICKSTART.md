# 🚀 Chatbot Authentication - Quick Start Guide

## Prerequisites
- Docker and Docker Compose installed
- Java 17+ installed
- Maven installed

## Step 1: Start Admin Backend

```bash
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml up -d

# Wait for services to start (30-60 seconds)
docker compose -f docker-compose-arm.yaml ps

# Check backend health
curl http://localhost:8080/actuator/health
```

**Expected output:**
```
backend         running
postgres        running
redis           running
...
```

## Step 2: Initialize Database (One-time setup)

```bash
# Apply schema
docker compose -f docker-compose-arm.yaml exec -T postgres psql -U admin -d admin < init/postgres/app-user-access.sql

# Verify users
docker compose -f docker-compose-arm.yaml exec postgres psql -U admin -d admin -c "SELECT email, full_name FROM simple_users;"
```

**Expected output:**
```
        email        |  full_name  
---------------------+-------------
 john@example.com    | John Doe
 jane@example.com    | Jane Smith
 test@example.com    | Test User
```

## Step 3: Build Chatbot

```bash
cd examples/chatbot
mvn clean package -DskipTests

# Verify JAR created
ls -lh target/chatbot-*.jar
```

## Step 4: Start Chatbot

### Option A: Maven (Development)
```bash
cd examples/chatbot
mvn spring-boot:run
```

### Option B: Java JAR (Production)
```bash
cd examples/chatbot
java -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

**Wait for:**
```
Started ChatbotApplication in X.XXX seconds
```

## Step 5: Test Authentication

### Terminal Test
```bash
cd examples/chatbot
./test-auth.sh
```

**Expected output:**
```
[1/6] Checking Admin backend...
✓ Admin backend is running at http://localhost:8080
[2/6] Checking Chatbot...
✓ Chatbot is running at http://localhost:8081
[3/6] Testing login API...
✓ Login successful for: john@example.com
[4/6] Testing access control...
✓ User has access to app: 2021941639059140610
[5/6] Testing user apps list...
✓ User has access to 1 app(s)
[6/6] Testing chatbot login UI...
✓ Login UI is accessible

All Tests Passed!
```

### Browser Test

1. **Open:** http://localhost:8081/
2. **Login with:**
   - Email: `john@example.com`
   - Password: `12345`
3. **Expected:** Redirect to chat interface
4. **Send a message:** Type "Hello" and press Send
5. **Expected:** Get AI response from the Admin app

## Demo Accounts

| Email | Password | Apps |
|-------|----------|------|
| john@example.com | 12345 | 1 app |
| jane@example.com | 12345 | 1 app |
| test@example.com | 12345 | 1 app |

All users have access to app ID: `2021941639059140610`

## API Endpoints

### Chatbot APIs (http://localhost:8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/login` | POST | Login with email/password |
| `/api/auth/logout` | POST | Logout and clear session |
| `/api/auth/session` | GET | Get current session info |
| `/api/chat/stream` | POST | Send chat message (streaming) |
| `/api/chat/conversation` | GET | Get current conversation ID |

### Admin APIs (http://localhost:8080)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/console/v1/chatbot/login` | POST | Login (returns JWT) |
| `/console/v1/chatbot/check-access` | GET | Check app access |
| `/console/v1/chatbot/user-apps` | GET | List user's apps |
| `/console/v1/chatbot/app-access` | POST | Grant app access (admin) |
| `/console/v1/chatbot/app-users` | GET | List app's users |
| `/console/v1/apps/chat/completions` | POST | Send chat (requires JWT) |

## Manual API Testing

### 1. Login
```bash
curl -X POST http://localhost:8080/console/v1/chatbot/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "12345"
  }' | jq '.'
```

**Save the token from response:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "email": "john@example.com",
    "fullName": "John Doe",
    "appIds": ["2021941639059140610"]
  }
}
```

### 2. Check Access
```bash
curl "http://localhost:8080/console/v1/chatbot/check-access?email=john@example.com&appId=2021941639059140610" | jq '.'
```

### 3. Send Chat (with JWT)
```bash
TOKEN="eyJhbGciOiJIUzUxMiJ9..."  # Replace with actual token

curl -X POST "http://localhost:8080/console/v1/apps/chat/completions" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "2021941639059140610",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": false,
    "conversation_id": ""
  }' | jq '.'
```

## Troubleshooting

### Issue: Login fails with "Invalid credentials"
**Solution:** Check if database was initialized
```bash
docker compose -f docker-compose-arm.yaml exec postgres psql -U admin -d admin -c "SELECT * FROM simple_users;"
```

### Issue: "No apps available"
**Solution:** Grant app access
```bash
curl -X POST http://localhost:8080/console/v1/chatbot/app-access \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "2021941639059140610",
    "userEmails": ["john@example.com"]
  }'
```

### Issue: Chatbot won't start on port 8081
**Solution:** Check if port is already in use
```bash
lsof -i :8081
# Kill existing process if needed
kill -9 <PID>
```

### Issue: "Not authenticated" error in chat
**Solution:** 
1. Check session is active: `curl http://localhost:8081/api/auth/session`
2. Try logging out and in again
3. Check browser console for errors

### Issue: Chat response is slow or times out
**Solution:**
1. Verify Admin backend is running and healthy
2. Check if the app exists in Admin UI (http://localhost:8000)
3. Verify the LLM model is configured correctly

## View Chat History

1. Login to Admin UI: http://localhost:8000
   - Username: `saa`
   - Password: `123456`
2. Go to **Apps** → Select your app → **Conversations**
3. See all messages from chatbot users

## Architecture

```
┌─────────────────┐         ┌──────────────────┐
│   Browser       │         │   Chatbot        │
│  localhost:8081 │◄───────►│   (Spring Boot)  │
└─────────────────┘         └──────────────────┘
                                      │
                                      │ WebClient + JWT
                                      ▼
                            ┌──────────────────┐
                            │   Admin Backend  │
                            │   localhost:8080 │
                            └──────────────────┘
                                      │
                                      ▼
                            ┌──────────────────┐
                            │   PostgreSQL     │
                            │   (Users, Apps,  │
                            │   Conversations) │
                            └──────────────────┘
```

## Next Steps

1. ✅ **Test authentication** - Follow browser test above
2. ✅ **Send chat messages** - Verify responses from AI
3. ✅ **Check chat history** - View in Admin UI
4. 🔧 **Add more users** - Insert into `simple_users` table
5. 🔧 **Create more apps** - Use Admin UI
6. 🔧 **Grant access** - Use `/app-access` endpoint

## Security Notes

⚠️ **Development Mode:**
- JWT secret is hardcoded (change in production)
- Passwords are "12345" (use strong passwords in production)
- Session timeout is 24h (adjust as needed)
- No HTTPS (use HTTPS in production)

🔒 **Production Checklist:**
- [ ] Change JWT secret in `application.yml`
- [ ] Enforce strong password policy
- [ ] Enable HTTPS/TLS
- [ ] Use Redis for session storage
- [ ] Add rate limiting
- [ ] Enable CORS properly
- [ ] Add request logging
- [ ] Set up monitoring

## Files Created

### Java Services
- `AdminApiService.java` - Communicates with Admin backend
- `AuthController.java` - Handles login/logout/session
- `ChatController.java` - Handles authenticated chat

### Frontend
- `static/index.html` - Login page
- `static/chat.html` - Chat interface

### Configuration
- `application.yml` - Updated with admin API URL
- `pom.xml` - Added JWT dependencies

### Testing
- `test-auth.sh` - End-to-end test script

### Documentation
- `AUTHENTICATION_GUIDE.md` - Complete implementation guide
- `QUICKSTART.md` - This file

---

**Need help?** Check logs:
- Chatbot: `examples/chatbot/chatbot.log` or console output
- Admin Backend: `docker compose -f docker-compose-arm.yaml logs backend`
- PostgreSQL: `docker compose -f docker-compose-arm.yaml logs postgres`
