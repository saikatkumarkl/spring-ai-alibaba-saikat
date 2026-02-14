# Chatbot Authentication & Authorization Implementation Guide

This guide shows how to add user authentication, app-level authorization, and chat history to the chatbot example.

## Overview

**What we're building:**
1. Login screen for chatbot (email + password "12345")
2. Admin apps with user email whitelist
3. Users can only access apps they're authorized for
4. All chat history is saved to database
5. Full API testing

---

## Step 1: Database Setup ✅

**File created:** `spring-ai-alibaba-admin/docker/middleware/init/postgres/app-user-access.sql`

**Tables created:**
- `simple_users` - User accounts (email, password_hash, full_name)
- `app_user_access` - Which users can access which apps

**Demo users created:**
| Email | Password | Full Name |
|-------|----------|-----------|
| john@example.com | 12345 | John Doe |
| jane@example.com | 12345 | Jane Smith |
| test@example.com | 12345 | Test User |

**Apply to database:**
```bash
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml exec -T postgres psql -U admin -d admin < init/postgres/app-user-access.sql
```

---

## Step 2: Admin Backend APIs ✅

**File created:** `ChatbotAuthController.java`

**Endpoints added:**

### POST `/console/v1/chatbot/login`
Login and get JWT token
```bash
curl -X POST http://localhost:8080/console/v1/chatbot/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "12345"
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGc....",
    "email": "john@example.com",
    "fullName": "John Doe",
    "appIds": ["2021941639059140610"]
  }
}
```

### GET `/console/v1/chatbot/check-access`
Check if user has access to app
```bash
curl "http://localhost:8080/console/v1/chatbot/check-access?email=john@example.com&appId=2021941639059140610"
```

### GET `/console/v1/chatbot/user-apps`
Get all apps user can access
```bash
curl "http://localhost:8080/console/v1/chatbot/user-apps?email=john@example.com"
```

### POST `/console/v1/chatbot/app-access`
Update app access for users (Admin feature)
```bash
curl -X POST http://localhost:8080/console/v1/chatbot/app-access \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "2021941639059140610",
    "userEmails": ["john@example.com", "jane@example.com"]
  }'
```

### GET `/console/v1/chatbot/app-users`
Get users who have access to app
```bash
curl "http://localhost:8080/console/v1/chatbot/app-users?appId=2021941639059140610"
```

---

## Step 3: Rebuild Admin Backend

**Dependencies added:**
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT tokens)
- `spring-security-crypto` (BCrypt password hashing)

**Rebuild:**
```bash
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml up -d --build backend
```

---

## Step 4: Chatbot Login Screen

### 4.1 Add Dependencies to Chatbot

```xml
<!-- File: examples/chatbot/pom.xml -->
<dependencies>
    <!-- ...existing... -->
    
    <!-- WebFlux for Admin API calls -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- JWT parsing -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 4.2 Create AdminApiService

```java
// File: examples/chatbot/src/main/java/com/alibaba/cloud/ai/examples/chatbot/AdminApiService.java
package com.alibaba.cloud.ai.examples.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminApiService {

    private final WebClient webClient;

    public AdminApiService(@Value("${admin.api.base-url:http://localhost:8080}") String baseUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    /**
     * Login to Admin backend
     */
    public Mono<Map<String, Object>> login(String email, String password) {
        return webClient.post()
            .uri("/console/v1/chatbot/login")
            .bodyValue(Map.of("email", email, "password", password))
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> (Map<String, Object>) response.get("data"));
    }

    /**
     * Send chat message to Admin app
     */
    public Flux<String> chatStream(String appId, String token, String message, String conversationId) {
        Map<String, Object> requestBody = Map.of(
            "app_id", appId,
            "messages", List.of(Map.of("role", "user", "content", message)),
            "stream", true,
            "conversation_id", conversationId != null ? conversationId : ""
        );

        return webClient.post()
            .uri("/console/v1/apps/chat/completions")
            .header("Authorization", "Bearer " + token)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class);
    }
}
```

### 4.3 Create Auth Controller

```java
// File: examples/chatbot/src/main/java/com/alibaba/cloud/ai/examples/chatbot/AuthController.java
package com.alibaba.cloud.ai.examples.chatbot;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminApiService adminApi;

    public AuthController(AdminApiService adminApi) {
        this.adminApi = adminApi;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest request, HttpSession session) {
        return adminApi.login(request.getEmail(), request.getPassword())
            .doOnNext(data -> {
                // Store in session
                session.setAttribute("user", data);
                session.setAttribute("token", data.get("token"));
                session.setAttribute("email", data.get("email"));
                log.info("User logged in: {}", data.get("email"));
            });
    }

    @PostMapping("/logout")
    public Mono<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return Mono.just(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/session")
    public Mono<Map<String, Object>> getSession(HttpSession session) {
        Object user = session.getAttribute("user");
        if (user != null) {
            return Mono.just((Map<String, Object>) user);
        }
        return Mono.error(new RuntimeException("Not logged in"));
    }
}
```

### 4.4 Create Chat Controller

```java
// File: examples/chatbot/src/main/java/com/alibaba/cloud/ai/examples/chatbot/ChatController.java
package com.alibaba.cloud.ai.examples.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AdminApiService adminApi;
    private final Map<String, String> sessionConversations = new ConcurrentHashMap<>();

    public ChatController(AdminApiService adminApi) {
        this.adminApi = adminApi;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message, HttpSession session) {
        String token = (String) session.getAttribute("token");
        String email = (String) session.getAttribute("email");
        Map<String, Object> user = (Map<String, Object>) session.getAttribute("user");

        if (token == null || user == null) {
            return Flux.error(new RuntimeException("Not authenticated"));
        }

        // Get first app the user has access to
        java.util.List<String> appIds = (java.util.List<String>) user.get("appIds");
        if (appIds == null || appIds.isEmpty()) {
            return Flux.error(new RuntimeException("No apps available"));
        }

        String appId = appIds.get(0);
        String conversationId = sessionConversations.get(session.getId());

        return adminApi.chatStream(appId, token, message, conversationId)
            .doOnNext(chunk -> {
                // Extract conversation_id from first chunk
                if (conversationId == null && chunk.contains("\"conversation_id\":")) {
                    String extracted = extractConversationId(chunk);
                    if (extracted != null) {
                        sessionConversations.put(session.getId(), extracted);
                        log.info("New conversation: {} for session: {}", extracted, session.getId());
                    }
                }
            });
    }

    private String extractConversationId(String sseChunk) {
        try {
            int start = sseChunk.indexOf("\"conversation_id\":\"") + 19;
            int end = sseChunk.indexOf("\"", start);
            return sseChunk.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
```

### 4.5 Create Login HTML

```html
<!-- File: examples/chatbot/src/main/resources/static/login.html -->
<!DOCTYPE html>
<html>
<head>
    <title>Chatbot Login</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        .login-container {
            background: white;
            padding: 40px;
            border-radius: 10px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            width: 100%;
            max-width: 400px;
        }
        h1 {
            text-align: center;
            color: #333;
            margin-bottom: 30px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 5px;
            color: #555;
            font-weight: 500;
        }
        input {
            width: 100%;
            padding: 12px;
            border: 1px solid #ddd;
            border-radius: 5px;
            font-size: 14px;
            box-sizing: border-box;
        }
        input:focus {
            outline: none;
            border-color: #667eea;
        }
        button {
            width: 100%;
            padding: 12px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 5px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.3s;
        }
        button:hover {
            background: #5568d3;
        }
        .error {
            color: #e74c3c;
            font-size: 14px;
            margin-top: 10px;
            display: none;
        }
        .info {
            text-align: center;
            margin-top: 20px;
            font-size: 13px;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <h1>🤖 Chatbot Login</h1>
        <form id="loginForm">
            <div class="form-group">
                <label for="email">Email</label>
                <input type="email" id="email" name="email" required 
                       placeholder="john@example.com">
            </div>
            <div class="form-group">
                <label for="password">Password</label>
                <input type="password" id="password" name="password" required 
                       placeholder="12345">
            </div>
            <button type="submit">Login</button>
            <div class="error" id="error"></div>
        </form>
        <div class="info">
            Demo accounts: john@example.com, jane@example.com, test@example.com<br>
            Password: 12345
        </div>
    </div>

    <script>
        document.getElementById('loginForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const email = document.getElementById('email').value;
            const password = document.getElementById('password').value;
            const errorDiv = document.getElementById('error');
            
            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password })
                });
                
                const data = await response.json();
                
                if (data.token) {
                    // Redirect to chat UI
                    window.location.href = '/chatui/index.html';
                } else {
                    errorDiv.textContent = 'Invalid credentials';
                    errorDiv.style.display = 'block';
                }
            } catch (error) {
                errorDiv.textContent = 'Login failed. Please try again.';
                errorDiv.style.display = 'block';
            }
        });
    </script>
</body>
</html>
```

### 4.6 Update Application Properties

```yaml
# File: examples/chatbot/src/main/resources/application.yml
admin:
  api:
    base-url: http://localhost:8080

server:
  port: 8081
  servlet:
    session:
      timeout: 24h

spring:
  application:
    name: chatbot-example
```

---

## Step 5: Test End-to-End

### 5.1 Start Services

```bash
# Terminal 1: Start Admin backend
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml up -d

# Terminal 2: Start chatbot
cd examples/chatbot
mvn spring-boot:run
```

### 5.2 Test Login API

```bash
# Login as John
curl -X POST http://localhost:8080/console/v1/chatbot/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "12345"
  }' | jq '.'
```

**Expected response:**
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

### 5.3 Test Access Control

```bash
# Check if John has access to app
curl "http://localhost:8080/console/v1/chatbot/check-access?email=john@example.com&appId=2021941639059140610" | jq '.'

# Get John's apps
curl "http://localhost:8080/console/v1/chatbot/user-apps?email=john@example.com" | jq '.'
```

### 5.4 Test Chat with Authentication

```bash
# Save token from login
TOKEN="eyJhbGciOiJIUzUxMiJ9..."

# Send chat message
curl -X POST "http://localhost:8080/console/v1/apps/chat/completions" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "2021941639059140610",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false,
    "conversation_id": ""
  }' | jq '.'
```

### 5.5 Test UI Flow

1. **Open chatbot:** http://localhost:8081/login.html
2. **Login with:**
   - Email: `john@example.com`
   - Password: `12345`
3. **Should redirect to:** http://localhost:8081/chatui/index.html
4. **Send a message** → it uses the Admin app
5. **Check conversation history in Admin UI:**
   - Go to http://localhost:8000
   - Login as `saa / 123456`
   - View conversation logs

---

## Step 6: Add User Management to Admin UI

### 6.1 Add Email Field to App Creation

Add this to the Admin UI app creation form:

```tsx
// In Admin UI app form
<Form.Item
  label="Authorized Users (Emails)"
  name="userEmails"
  help="Comma-separated list of emails who can access this app"
>
  <Input.TextArea 
    placeholder="john@example.com, jane@example.com"
    rows={3}
  />
</Form.Item>
```

### 6.2 Save Authorized Users on App Creation

```typescript
// On app save
const userEmails = values.userEmails
  ?.split(',')
  .map(email => email.trim())
  .filter(email => email);

if (userEmails && userEmails.length > 0) {
  await fetch('/console/v1/chatbot/app-access', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      appId: savedApp.id,
      userEmails: userEmails
    })
  });
}
```

---

## Chat History

**Automatic:** All conversations are saved in the `conversation` and `conversation_message` tables by the Admin backend. No additional code needed!

**To view:**
1. Login to Admin UI (http://localhost:8000)
2. Go to the app's conversation logs
3. See all messages from chatbot users

---

## Security Notes

1. **Password:** Currently hardcoded to "12345" for demo. In production, use proper password validation.
2. **JWT Secret:** Change `chatbot.jwt.secret` in production
3. **HTTPS:** Use HTTPS in production for token transmission
4. **Session Management:** Sessions are stored in memory. Use Redis for production.

---

## Troubleshooting

**Issue:** "Not authenticated" error
- **Fix:** Check if session is active, token is valid

**Issue:** "No apps available"
- **Fix:** Grant app access using `/console/v1/chatbot/app-access` endpoint

**Issue:** Can't login
- **Fix:** Check if user exists in `simple_users` table and password hash is correct

---

## Next Steps

1. Add "Forgot Password" flow
2. Add user registration
3. Add role-based access (admin, user, viewer)
4. Add app-level permissions (read, write, admin)
5. Add rate limiting per user
6. Add audit logs for sensitive operations
