# Chatbot Authentication System

## Overview

This implements a complete end-to-end authentication system for chatbot applications that connect to the Spring AI Alibaba Admin backend. The system provides:

- User authentication via email/password
- JWT token-based session management
- App-level access control
- Database-backed user storage

## Architecture

```
Chatbot Frontend (HTML/JS)
    ↓ POST /console/v1/chatbot/login
Admin Backend (Spring Boot)
    ↓ Query
PostgreSQL Database
    - simple_users table (users + password hashes)
    - app_user_access table (app permissions)
    ↓ Return JWT token
Chatbot Frontend
    → Stores token in localStorage
    → Sends token with all API requests
```

## Database Schema

### `simple_users` Table
```sql
CREATE TABLE simple_users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);
```

### `app_user_access` Table
```sql
CREATE TABLE app_user_access (
    id SERIAL PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(app_id, user_email)
);
```

## API Endpoints

All endpoints are under `/console/v1/chatbot/` and **excluded from the main authentication interceptor**.

### 1. Login
```bash
POST /console/v1/chatbot/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "test123"
}

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "email": "john@example.com",
    "fullName": "John Doe",
    "appIds": []
  }
}
```

### 2. Check Access
```bash
GET /console/v1/chatbot/check-access?email=john@example.com&appId=my-app

Response:
{
  "code": 200,
  "message": "success",
  "data": true
}
```

### 3. Get User Apps
```bash
GET /console/v1/chatbot/user-apps?email=john@example.com

Response:
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "app_id": "app-123",
      "name": "My Chatbot",
      "description": "Description",
      "type": "CHAT"
    }
  ]
}
```

### 4. Manage App Access (Admin)
```bash
POST /console/v1/chatbot/app-access
Content-Type: application/json

{
  "appId": "app-123",
  "userEmails": ["john@example.com", "jane@example.com"]
}

Response:
{
  "code": 200,
  "message": "success",
  "data": "App access updated successfully"
}
```

### 5. Get App Users
```bash
GET /console/v1/chatbot/app-users?appId=app-123

Response:
{
  "code": 200,
  "message": "success",
  "data": ["john@example.com", "jane@example.com"]
}
```

### 6. Validate Token
```bash
POST /console/v1/chatbot/validate-token
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "email": "john@example.com",
    "valid": true
  }
}
```

### 7. Generate Password Hash (Debug)
```bash
GET /console/v1/chatbot/hash?password=test123

Response:
{
  "code": 200,
  "message": "success",
  "data": "$2a$10$pE3JIrc2n.BMQhsjf6xM.eBoyx6SGL88eQxEgpV2Rj7VJKoI2PviK"
}
```

## Demo Users

The following users are created by the init script (`postgres/init-simple-users.sql`):

| Email | Password | Full Name |
|-------|----------|-----------|
| `john@example.com` | `test123` | John Doe |
| `jane@example.com` | `test123` | Jane Smith |
| `admin@example.com` | `test123` | Admin User |

## Configuration

### Backend Configuration

**File:** `spring-ai-alibaba-admin-server-start/src/main/resources/application.yml`

```yaml
chatbot:
  jwt:
    secret: my-super-secret-key-change-in-production  # Change in production!
```

**Environment Variable:**
```bash
export CHATBOT_JWT_SECRET="your-production-secret-here"
```

### Interceptor Exclusion

**File:** `spring-ai-alibaba-admin-server-start/.../InterceptorConfig.java`

```java
registry.addInterceptor(tokenInterceptor)
    .addPathPatterns("/**")
    .excludePathPatterns(
        "/console/v1/chatbot/**",  // Chatbot auth endpoints
        "/console/v1/user/login",
        // ... other exclusions
    );
```

## Security Notes

1. **JWT Secret:** The default secret is for development only. In production, use a strong random secret at least 256 bits (32 bytes).

2. **Password Hashing:** All passwords are hashed with BCrypt (strength 10). The hash includes a random salt, so the same password will have different hashes each time.

3. **Token Expiration:** JWT tokens expire after 24 hours. After expiration, users must log in again.

4. **HTTPS:** In production, always use HTTPS to protect credentials and tokens in transit.

5. **Debug Endpoint:** The `/hash` endpoint is for development only. Remove it in production.

## Setup Instructions

### 1. Initialize Database

```bash
# Run the init script
docker exec -i postgres psql -U admin -d admin < \
  spring-ai-alibaba-admin/docker/middleware/postgres/init-simple-users.sql
```

### 2. Build and Deploy

```bash
# Build backend
cd spring-ai-alibaba-admin
mvn -B package -DskipTests -pl spring-ai-alibaba-admin-server-start -am

# Rebuild Docker container
cd docker/middleware
docker compose -f docker-compose-arm.yaml up -d --build backend
```

### 3. Test Login

```bash
curl -X POST http://localhost:8080/console/v1/chatbot/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"test123"}' | jq .
```

Expected response:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGci...",
    "email": "john@example.com",
    "fullName": "John Doe",
    "appIds": []
  }
}
```

## Frontend Integration

See `examples/chatbot/src/main/resources/static/index.html` for a complete example.

### Login Flow

```javascript
// 1. Login
const response = await fetch('http://localhost:8080/console/v1/chatbot/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});

const result = await response.json();
if (result.code === 200) {
  // 2. Store token
  localStorage.setItem('authToken', result.data.token);
  localStorage.setItem('userEmail', result.data.email);
  
  // 3. Use token for subsequent requests
  fetch('http://localhost:8080/console/v1/chat/messages', {
    headers: { 'Authorization': `Bearer ${result.data.token}` }
  });
}
```

## Troubleshooting

### Issue: "Invalid email or password"
- Verify the user exists: `SELECT * FROM simple_users WHERE email = 'john@example.com';`
- Regenerate password hash: `curl "http://localhost:8080/console/v1/chatbot/hash?password=test123"`
- Update database: `UPDATE simple_users SET password_hash = '...' WHERE email = 'john@example.com';`

### Issue: "Encoded password does not look like BCrypt"
- Check for whitespace: `UPDATE simple_users SET password_hash = trim(password_hash);`
- Verify hash starts with `$2a$10$` or `$2b$10$`

### Issue: "Illegal base64 character" in JWT
- The JWT secret must be at least 256 bits (32 characters)
- Use `Keys.hmacShaKeyFor()` to create the signing key from the secret string
- Fixed in JJWT 0.12.x by using the new `verifyWith()` API

### Issue: 401 Unauthorized from Admin backend
- Verify `/console/v1/chatbot/**` is excluded in InterceptorConfig
- Check backend logs: `docker compose -f docker-compose-arm.yaml logs backend | grep -i chatbot`

## Files Modified

### Backend Files
- `spring-ai-alibaba-admin-server-start/src/main/java/.../ChatbotAuthController.java` - New REST controller
- `spring-ai-alibaba-admin-server-start/src/main/java/.../InterceptorConfig.java` - Added `/console/v1/chatbot/**` exclusion

### Database Files
- `spring-ai-alibaba-admin/docker/middleware/postgres/init-simple-users.sql` - New database initialization script

### Frontend Files
- `examples/chatbot/src/main/resources/static/index.html` - Login UI
- `examples/chatbot/src/main/resources/static/chat.html` - Chat UI (uses JWT token)

### Configuration Files
- `examples/chatbot/pom.xml` - Added Lombok dependency
- `spring-ai-alibaba-admin-server-start/pom.xml` - (No changes needed, JWT already in dependencies)

## Dependencies

The chatbot auth system uses:

- **Spring Security Crypto** - BCrypt password hashing
- **JJWT 0.12.6** - JWT token generation and validation
- **Spring Data JDBC** - Database access via JdbcTemplate
- **PostgreSQL** - User and access control storage

All dependencies are already included in the Admin backend.

## Next Steps

To add user management UI in the Admin platform:

1. Add an "Authorized Users" field to the App creation/edit form in the Admin UI
2. Call `/console/v1/chatbot/app-access` endpoint when saving app settings
3. Display current users via `/console/v1/chatbot/app-users` endpoint
4. Allow multi-select or comma-separated email input

See the Copilot instructions for details on the Admin UI architecture (React + UmiJS in `spring-ai-alibaba-admin/frontend/`).
