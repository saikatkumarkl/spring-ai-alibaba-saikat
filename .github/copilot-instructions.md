# Copilot Instructions for Spring AI Alibaba

## Project Overview

Spring AI Alibaba is a **production-ready multi-agent framework** built on Spring AI. It provides agent orchestration, workflow management, and context engineering for AI-powered Java applications.

## Architecture (Key Modules)

| Module | Purpose |
|--------|---------|
| `spring-ai-alibaba-agent-framework` | High-level agent APIs (`ReactAgent`, `SequentialAgent`, `ParallelAgent`, `LoopAgent`, `LlmRoutingAgent`) |
| `spring-ai-alibaba-graph-core` | Low-level runtime: `StateGraph`, `Node`, `Edge`, `OverAllState`, persistence, checkpointing |
| `spring-boot-starters/` | Nacos integration (A2A, config), observability, built-in nodes |
| `spring-ai-alibaba-studio` | Embedded debugging UI for visualizing agents |
| `spring-ai-alibaba-admin` | Visual agent platform with React frontend (packages: main, spark-flow, spark-i18n) |
| `spring-ai-alibaba-sandbox-tool` | Sandboxed execution environment for tools |

**Data flow:** `ReactAgent` → compiles to `StateGraph` → executes via `CompiledGraph` → checkpoints to savers (Memory, Redis, PostgreSQL, MySQL, MongoDB, Oracle, File)

**Agent Framework abstraction:** The Agent Framework is built atop Graph, abstracting away complexities through concepts like `ReactAgent` and `SequentialAgent`. Graph provides atomic components with high flexibility but higher learning costs.

## Build Commands

```bash
# Build entire project (skip tests for speed)
./mvnw -B package -DskipTests=true

# Build specific module
./mvnw -pl :spring-ai-alibaba-agent-framework -B package -DskipTests=true

# Clean and build
./mvnw clean package

# Run tests
./mvnw test

# Run specific test class
./mvnw -pl :<module> -Dtest=<TestClass> test

# Code formatting (auto-applies Spring standards)
mvn spotless:apply

# Linting (from project root)
make lint                 # yaml-lint, codespell, newline-check
make licenses-check       # Verify Apache 2.0 headers
make licenses-fix         # Auto-fix missing Apache 2.0 headers
```

## Code Patterns

### Creating a ReactAgent (Primary Pattern)
```java
ReactAgent agent = ReactAgent.builder()
    .name("my_agent")
    .model(chatModel)                    // ChatModel from Spring AI
    .instruction("System prompt here")
    .tools(tool1, tool2)                 // ToolCallback instances
    .hooks(hook1, hook2)                 // Context engineering hooks
    .saver(new MemorySaver())            // Checkpointing
    .build();
```

### Multi-Agent Orchestration
```java
// Sequential execution
SequentialAgent.builder().subAgents(agent1, agent2).build();

// Parallel execution
ParallelAgent.builder().subAgents(agent1, agent2).build();

// LLM-based routing
LlmRoutingAgent.builder().model(chatModel).subAgents(agent1, agent2).build();
```

### Context Engineering Hooks
Located in `com.alibaba.cloud.ai.graph.agent.hook`:
- `SummarizationHook` - Message compression
- `HumanInTheLoopHook` - Approval workflows
- `ShellToolAgentHook` - Shell session lifecycle (required when using `ShellTool`)

### Checkpointing (Persistence)
```java
// In-memory (development)
.saver(new MemorySaver())

// Production options in com.alibaba.cloud.ai.graph.checkpoint.savers:
// PostgresSaver, MysqlSaver, RedisSaver, MongoSaver, OracleSaver, FileSystemSaver
```

## Model Support

The framework is **model-agnostic**—any Spring AI `ChatModel` works:
- **DashScope** (Alibaba Cloud) - default in examples, requires `AI_DASHSCOPE_API_KEY`
- **OpenAI** - use `spring-ai-starter-model-openai`
- **DeepSeek** - use `spring-ai-starter-model-deepseek`
- **Ollama** - for local models

See [spring-ai-alibaba-admin/model-config-*.yaml](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/) for provider configuration templates.

## Conventions

- **JDK 17** required; use records, switch expressions, text blocks
- **Spring Boot 3.5.x** with `jakarta.*` namespace (not `javax.*`)
- **Apache 2.0 license headers** on all Java files
- Use **Lombok** (`@Slf4j`, `@Data`) to reduce boilerplate
- Avoid `System.out.println`; use SLF4J logging
- Define agents as Spring `@Bean` in `@Configuration` classes
- Tools are `ToolCallback` instances (use `FunctionToolCallback.builder()`)

## Key Files to Reference

- [examples/chatbot/](examples/chatbot/) - Complete agent example with tools
- [ReactAgent.java](spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/ReactAgent.java) - Main agent builder
- [StateGraph.java](spring-ai-alibaba-graph-core/src/main/java/com/alibaba/cloud/ai/graph/StateGraph.java) - Workflow definition
- [HooksExample.java](examples/documentation/src/main/java/com/alibaba/cloud/ai/examples/documentation/framework/tutorials/HooksExample.java) - Hook usage patterns

## Testing

- **JUnit 5** + **Mockito** for unit testing
- API key for tests: `export AI_DASHSCOPE_API_KEY=your-key`
- Run specific test: `./mvnw -pl :<module> -Dtest=<TestClass> test`
- Test pattern: Use `@Test` annotation; avoid commented tests without explanation
- Mock ChatModel when testing agent logic without live API calls

## Full-Stack Development (Frontend + Backend)

### Backend (Spring Boot Admin Server)

Located in `spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/`:

```bash
# Start backend server (Java 17 required)
cd spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start
./mvnw spring-boot:run

# Server runs at http://localhost:8080
# API docs at http://localhost:8080/swagger-ui.html
```

**Environment setup:** Configure in `application.yml` or use environment variables (see Environment Configuration section). Requires MySQL, Redis, Elasticsearch for full functionality.

### Frontend (React Admin UI)

Located in `spring-ai-alibaba-admin/frontend/`:
- **Framework:** React 18 + UmiJS 4 + TypeScript
- **Packages:** Monorepo (main workbench, spark-flow editor, spark-i18n)
- **Prerequisites:** Node.js >= v20

```bash
# Initial setup (from frontend/ directory)
npm install rimraf copyfiles cross-env --save-dev
npm run re-install              # Installs all workspace dependencies

# Configure environment (in packages/main/)
cd packages/main
cp .env.example .env
# Edit .env:
#   WEB_SERVER="http://127.0.0.1:8080"  # Backend URL
#   BACK_END="java"
#   DEFAULT_USERNAME=saa
#   DEFAULT_PASSWORD=123456

# Run dev server
npm run dev                     # Starts at http://localhost:8000

# Production build
npm run build:subtree:java      # Outputs to packages/main/dist
```

**Frontend architecture:**
- `packages/main/` - Main workbench (Agent/MCP/Plugin/Knowledge management)
- `packages/spark-flow/` - Visual workflow editor (XFlow-based, uses Zustand for state)
- `packages/spark-i18n/` - Internationalization (Chinese/English)

**Lint/Format:** `npm run lint` (ESLint + Prettier auto-fix)

## Running Full Stack

```bash
# Terminal 1: Backend + Middleware (Docker Compose)
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml up -d

# This starts:
# - MySQL (3306)
# - Redis (6379)
# - Elasticsearch (9200)
# - Nacos (8848)
# - RocketMQ (9876, 10909, 10911)
# - Backend (8080) with docker profile

# Terminal 2: Frontend
cd spring-ai-alibaba-admin/frontend/packages/main
npm run dev

# Access: http://localhost:8000 (frontend proxies API to :8080)
# Default credentials: saa / 123456
```

**Stop services:**
```bash
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml down
```

## Environment Configuration

Admin server uses environment variable overrides (see `CONFIGURATION.md`):
- MySQL: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Redis: `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_DATABASE`
- Elasticsearch: `SPRING_ELASTICSEARCH_URIS`, `SPRING_ELASTICSEARCH_URL`
- Nacos: `NACOS_SERVER_ADDR`
- RocketMQ: `ROCKETMQ_ENDPOINTS`, `ROCKETMQ_NAME_SERVER`

**Spring Profiles:**
- `local` - For local development with services on localhost (use when running services manually)
- `docker` - For Docker Compose deployment (services referenced by container names)
- `dev` - Points to remote development server (47.239.212.78)

**Docker Setup:** 
- Backend Dockerfile: `spring-ai-alibaba-admin-server-start/Dockerfile` (multi-stage build)
- Simple Dockerfile: `spring-ai-alibaba-admin/docker/middleware/Dockerfile.backend` (uses pre-built JAR)
- Docker Compose: `spring-ai-alibaba-admin/docker/middleware/docker-compose-arm.yaml`
- Backend runs with `SPRING_PROFILES_ACTIVE=docker` to use container service names
- Rebuild backend: `docker compose -f docker-compose-arm.yaml up -d --build backend`

## Environment Configuration (Non-Docker)

When running backend manually (not with Docker Compose):
- MySQL: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Redis: `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_DATABASE`
- Elasticsearch: `SPRING_ELASTICSEARCH_URIS`, `SPRING_ELASTICSEARCH_URL`
- Nacos: `NACOS_SERVER_ADDR`
- RocketMQ: `ROCKETMQ_ENDPOINTS`, `ROCKETMQ_NAME_SERVER`
- Default values in `application.yml` are for localhost development

## Common Gotchas

1. When using `ShellTool`, always add `ShellToolAgentHook` to manage session lifecycle
2. Dependencies are managed via `spring-ai-alibaba-bom`—check parent pom for versions
3. Graph constants: `StateGraph.START`, `StateGraph.END`, `StateGraph.ERROR`
4. State keys use strategies: `AppendStrategy`, `ReplaceStrategy` in `com.alibaba.cloud.ai.graph.state.strategy`
5. Admin module has separate frontend build—run `npm` commands from `frontend/` directory
6. License headers must use correct year range (check existing files for pattern)
7. When creating agents, always define as `@Bean` in `@Configuration` class for Spring injection

## Debugging

- **Spring AI Alibaba Studio:** Embedded UI at `/studio` endpoint for visualizing agent execution
- **Admin Platform:** Full-featured visual debugging at `http://localhost:8080` (default)
- **Graph visualization:** Export workflows to PlantUML/Mermaid for architecture review
- **Checkpointing:** Use savers for debugging state transitions and resuming failed workflows
