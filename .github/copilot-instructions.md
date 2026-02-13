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

## ManifoldCF (Document Crawler)

Apache ManifoldCF is integrated as a document crawler that pushes content to OpenSearch. Located in `manifoldcf-saikat/`.

### Build (Maven — converted from Ant)

**Ant build files and Ant-produced artifacts have been removed.** Only the Maven build is supported.

```bash
# Full local build (creates distribution in distribution/target/dist/)
cd manifoldcf-saikat
mvn -B install -DskipTests -Dmaven.test.skip -DskipITs -Drat.skip -pl distribution -am

# Build time: ~2 minutes first run, ~2 seconds incremental
```

Key flags:
- `-DskipTests` — skip test execution but still build test-jars (required by some modules)
- `-Dmaven.test.skip` — don't compile tests
- `-DskipITs` — skip failsafe integration tests (alfresco-webscript needs this)
- `-Drat.skip` — skip Apache RAT license checks
- `-pl distribution -am` — build only the distribution module and its dependencies

### Docker Image

```bash
# Build Docker image (uses local distribution, no Maven inside Docker)
cd manifoldcf-saikat
docker build -f Dockerfile.maven -t manifoldcf-saikat:latest .

# Build time: ~5 seconds (copies pre-built dist/)
```

The Dockerfile (`Dockerfile.maven`) is a single-stage build that copies `distribution/target/dist/` into the image. **Always run the Maven build first.**

**Release scripts are Maven-only** (no Ant calls):
- `create-release-candidate.sh`
- `create-release-candidate-only-artifacts.sh`

### Deploy with Docker Compose

```bash
# Start ManifoldCF + dependencies (postgres, opensearch)
cd spring-ai-alibaba-admin/docker/middleware
docker compose -f docker-compose-arm.yaml up -d manifoldcf

# Rebuild after code changes
docker compose -f docker-compose-arm.yaml up -d --build manifoldcf

# View logs
docker compose -f docker-compose-arm.yaml logs -f manifoldcf
```

- **UI:** http://localhost:8345/mcf-crawler-ui/
- **API:** http://localhost:8345/mcf-api-service/json/
- **Default credentials:** admin / admin (configured in properties.xml)

### Fast Iteration Cycle

```bash
cd manifoldcf-saikat && mvn -B install -DskipTests -Dmaven.test.skip -DskipITs -Drat.skip -pl distribution -am \
  && cd ../spring-ai-alibaba-admin/docker/middleware \
  && docker compose -f docker-compose-arm.yaml up -d --build manifoldcf
```

### Configuration Files

All Docker config is in `spring-ai-alibaba-admin/docker/middleware/manifoldcf/config/`:

| File | Purpose |
|------|---------|
| `properties.xml` | PostgreSQL connection, lib directories, authority settings |
| `jetty.xml` | Jetty server config (port 8345, thread pool) |
| `logging.xml` | Log4j logging configuration |
| `start-options.env.unix` | JVM options (JAVA_OPTS) |
| `connectors.xml` | Registered connector classes (auto-loaded on startup) |
| `opensearch-output.json` | JSON payload for the default OpenSearch output connection |
| `init-opensearch-output.sh` | Auto-creates OpenSearch output connection on startup |

### OpenSearch Output (Auto-Configured)

A `manifoldcf-init` sidecar service runs after ManifoldCF starts and automatically creates:
1. **OpenSearch output connection** (ElasticSearch connector type)
2. **Alfresco CMIS repository connection** (AtomPub binding over HTTPS)
3. **Crawl job** (CMIS → OpenSearch, `SELECT * FROM cmis:document`)

Configure CMIS credentials via environment variables in `docker-compose-arm.yaml`:
```yaml
- CMIS_USERNAME=admin
- CMIS_PASSWORD=admin
- CMIS_PROTOCOL=https
- CMIS_SERVER=alfresco-demo.crestsolution.com
- CMIS_PORT=8080
- CMIS_PATH=/alfresco/api/-default-/cmis/versions/1.1/atom
- CMIS_BINDING=atom
- CMIS_REPOSITORY_ID=-default-
```

To manually create/verify the connection:
```bash
# Check if connection exists
curl -s http://localhost:8345/mcf-api-service/json/outputconnections/OpenSearch | jq .

# Verify connection health
curl -s http://localhost:8345/mcf-api-service/json/status/outputconnections/OpenSearch | jq .

# Check CMIS repository connection
curl -s http://localhost:8345/mcf-api-service/json/status/repositoryconnections/Alfresco%20CMIS | jq .

# Check job status
curl -s http://localhost:8345/mcf-api-service/json/jobstatuses | jq .

# Start a job manually
curl -s -X PUT http://localhost:8345/mcf-api-service/json/start/<job_id>

# Check OpenSearch document count
curl -s http://localhost:9200/manifoldcf/_count | jq .
```

### CMIS Connector Fixes

The CMIS connector has two critical fixes for HTTPS reverse proxies and classloader isolation:

1. **HttpsForceHttpInvoker** (`connectors/cmis/connector/src/main/java/.../cmis/HttpsForceHttpInvoker.java`)
   - CMIS servers behind an HTTPS reverse proxy may return `http://` URLs in service documents/AtomPub responses
   - Apache Chemistry follows those internal HTTP URLs; the proxy rejects them with "400 Bad Request: The plain HTTP request was sent to HTTPS port"
   - `HttpsForceHttpInvoker` wraps `DefaultHttpInvoker` and rewrites all `http://` URLs to `https://` before sending requests
   - Automatically activated when CMIS connection uses `protocol=https` (wired via `SessionParameter.HTTP_INVOKER_CLASS`)

2. **Classloader-safe reflection in `getDocumentURL`** (`CmisRepositoryConnectorUtils.java`, `CmisOutputConnectorUtils.java`)
   - Original code used `AbstractAtomPubService.class.getDeclaredMethod(...)` then `method.invoke(objectService, ...)` — fails with `IllegalArgumentException: object is not an instance of declaring class` because ManifoldCF loads connector JARs in a separate classloader
   - Fix: `findMethodInHierarchy()` walks the actual class hierarchy of the `objectService` instance to find the `loadLink` method, avoiding classloader mismatch
   - Fallback: if reflection fails entirely, constructs document URI from folder path + filename

### ACL-Aware Document Indexing

ManifoldCF extracts CMIS ACLs at crawl time and stores them **raw** (groups + users, no expansion) in OpenSearch. Documents are synced with their full ACL data — no vendor-specific REST APIs are involved in the admin backend.

**Key insight:** Groups live in the **CMIS application** (Alfresco, FileNet, etc.), not in SSO providers. Using vendor-specific REST APIs to resolve groups at query time doesn't scale across vendors. Instead, all ACL data is synced at crawl time and stored in OpenSearch for direct querying.

**Architecture:**
```
Crawl time:  CMIS Server → [extractAndSetAcl()] → RepositoryDocument.setSecurity() → ElasticSearch Output → OpenSearch
                            (raw ACLs: groups stored as groups, users stored as users, all lowercased)

Query time:  OpenSearch query with ACL filter on allow_token_document / deny_token_document
```

**Crawl-Time ACL Extraction (ManifoldCF):**
1. `extractAndSetAcl()` in `CmisRepositoryConnector.java` calls `session.getBinding().getAclService().getAcl()` for each document
2. IMPORTANT: Cannot use `cmisObject.getAcl()` — returns null because default `OperationContext` has `includeACL=false`
3. `resolveAcesToTokens()` stores ALL principals as-is:
   - All principals are lowercased (e.g., `GROUP_site_demo-test-site_SiteManager` → `group_site_demo-test-site_sitemanager`)
   - No group detection, no group expansion, no external API calls
   - Both users and groups stored as allow/deny tokens
4. Document-level ACLs → `allow_token_document` / `deny_token_document`
5. Parent folder ACLs → `allow_token_parent` / `deny_token_parent`

**ACL data in OpenSearch (569 total documents, 32 unique ACL tokens):**
| Example Token | Type | Documents |
|---------------|------|-----------|
| `group_everyone` | Group | 451 |
| `group_site_demo-test-site_sitemanager` | Group | 48 |
| `jeevitha` | User | 15 |
| `admin` | User | varies |

**OpenSearch ACL query pattern:**
```json
{
  "query": {
    "bool": {
      "must": [ { "multi_match": { "query": "search terms", "fields": ["content", "file_title"] } } ],
      "filter": {
        "bool": {
          "should": [
            { "term": { "allow_token_document": "username" } },
            { "term": { "allow_token_document": "group_everyone" } }
          ],
          "minimum_should_match": 1,
          "must_not": [
            { "term": { "deny_token_document": "username" } }
          ]
        }
      }
    }
  }
}
```

**OpenSearch Index Template:**
- Template: `manifoldcf-acl` (priority 100, pattern `manifoldcf*`)
- ACL fields: `keyword` type with `lowercase` normalizer
- Created automatically by `init-opensearch-output.sh` (step 0)
- Template file: `spring-ai-alibaba-admin/docker/middleware/manifoldcf/config/opensearch-index-template.json`

**Connector-agnostic design:** The ACL token fields (`allow_token_*`, `deny_token_*`) are part of ManifoldCF's standard security model. Any connector (Google Drive, Dropbox, SharePoint, etc.) that calls `RepositoryDocument.setSecurity()` will have its ACLs indexed the same way. The CMIS connector is the first one with explicit ACL extraction.

**Re-indexing after enabling ACLs:**
```bash
# 1. Delete old index (has __nosecurity__ tokens)
curl -X DELETE http://localhost:9200/manifoldcf

# 2. Ensure index template exists
curl -X PUT http://localhost:9200/_index_template/manifoldcf-acl -H 'Content-Type: application/json' -d @opensearch-index-template.json

# 3. Delete old jobs and create fresh one (old job version tracking prevents re-ingestion)
# Use ManifoldCF UI at http://localhost:8345/mcf-crawler-ui/ or API

# 4. Start the new job
curl -X PUT http://localhost:8345/mcf-api-service/json/start/<job_id>
```

**Deprecated (dead code — kept for reference):**
The following files in `connectors/cmis/connector/src/main/java/.../cmis/` implemented the old crawl-time group expansion approach and are **no longer referenced** from `CmisRepositoryConnector`:
- `GroupMemberResolver.java`, `GroupMemberResolverFactory.java`
- `AlfrescoGroupMemberResolver.java`, `LdapGroupMemberResolver.java`, `HttpGroupMemberResolver.java`
- `TrustAllSSLSocketFactory.java`, `NoOpGroupMemberResolver.java`
- Environment variables `MCF_GROUP_RESOLVER`, `MCF_LDAP_*`, `MCF_HTTP_GROUP_RESOLVER_*` are no longer used

The following admin backend files were also removed (vendor-specific REST API approach):
- `CmisProperties.java`, `CmisUserGroupService.java`, `DocumentSearchService.java`, `DocumentSearchController.java`
- `cmis.*` properties in `application.yml` and `CMIS_*` env vars from backend service in `docker-compose-arm.yaml`

### ManifoldCF Gotchas

1. **ConfigParams is case-sensitive** — API parameter names must be UPPERCASE (`SERVERLOCATION`, `INDEXNAME`, `INDEXTYPE`) to match Java enum `.name()`
2. **Connector JARs must NOT go in `lib/`** — they belong in `connector-lib/` (separate classloader). Mixing causes `NoClassDefFoundError` at runtime
3. **Nuxeo connector excluded** — Maven repository unavailable, removed from distribution and `connectors.xml`
4. **JSP support** — `jetty-jsp-9.2.30.v20200428.jar` is required but not in Maven dependency tree (Ant-managed). Added as explicit dependency in `distribution/pom.xml`
5. **test-jar dependencies** — Use `-DskipTests` (not `-Dmaven.test.skip`) so test-jars still get built for modules that depend on them
6. **HTTPS reverse proxy** — When a CMIS server is behind HTTPS, service documents may return internal `http://` URLs. `HttpsForceHttpInvoker` rewrites these to `https://` automatically when `protocol=https` is set on the connection
7. **CMIS reflection classloader issue** — `getDocumentURL` uses reflection to call `AbstractAtomPubService.loadLink()`. ManifoldCF's connector classloader isolation breaks static class references; use `findMethodInHierarchy()` to walk the instance's actual class hierarchy instead
8. **CMIS binding types** — AtomPub (`atom`) is the most reliable binding for Alfresco. Browser binding (`browser`) may fail with "Invalid form encoding!" errors. WebServices (`ws`) is not recommended.
9. **CMIS ACL fetching** — `cmisObject.getAcl()` returns null because the default `OperationContext` has `includeACL=false`. Must use `session.getBinding().getAclService().getAcl(repositoryId, objectId, true, null)` for explicit ACL retrieval.
10. **Job version tracking prevents re-ingestion** — When you delete/recreate an OpenSearch index, you must also delete old ManifoldCF jobs. ManifoldCF tracks document versions per-output connection; if the same connection/document pair exists in history, the doc is skipped. Delete old jobs to force fresh ingestion.
11. **OpenSearch disk watermarks** — At 95% disk usage, OpenSearch's flood-stage watermark blocks all writes. ManifoldCF crawls will process documents but fail to index them (only 2 of 994 indexed). Fix: increase watermarks with `PUT _cluster/settings` — e.g., `flood_stage: 98%`, `high: 96%`, `low: 94%`. Use `?flat_settings=true` to verify.
12. **ManifoldCF logging** — SLF4J binds to log4j1 (`org.slf4j.impl.Log4jLoggerFactory`), NOT log4j2. The `logging.xml` in config uses log4j2 XML format but may not properly control connector-level logging. Connector logger name is `org.apache.manifoldcf.connectors` (defined in `framework/pull-agent/.../Logging.java`). For debugging, use `System.err.println()` or switch to log4j1 properties format.

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
