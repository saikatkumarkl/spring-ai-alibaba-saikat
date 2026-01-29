# Copilot Instructions for Spring AI Alibaba

## Project Overview

Spring AI Alibaba is a **production-ready multi-agent framework** built on Spring AI. It provides agent orchestration, workflow management, and context engineering for AI-powered Java applications.

## Architecture (Key Modules)

| Module | Purpose |
|--------|---------|
| `spring-ai-alibaba-agent-framework` | High-level agent APIs (`ReactAgent`, `SequentialAgent`, `ParallelAgent`, `LoopAgent`, `LlmRoutingAgent`) |
| `spring-ai-alibaba-graph-core` | Low-level runtime: `StateGraph`, `Node`, `Edge`, `OverAllState`, persistence, checkpointing |
| `spring-boot-starters/` | Nacos integration (A2A, config), observability, built-in nodes |
| `spring-ai-alibaba-studio` | Embedded debugging UI |
| `spring-ai-alibaba-admin` | Visual agent platform with frontend |

**Data flow:** `ReactAgent` → compiles to `StateGraph` → executes via `CompiledGraph` → checkpoints to savers (Memory, Redis, PostgreSQL, MySQL, MongoDB, Oracle, File)

## Build Commands

```bash
# Build entire project (skip tests for speed)
./mvnw -B package -DskipTests=true

# Build specific module
./mvnw -pl :spring-ai-alibaba-agent-framework -B package -DskipTests=true

# Run tests
./mvnw test

# Code formatting (auto-applies Spring standards)
mvn spotless:apply

# Linting (from project root)
make lint                 # yaml-lint, codespell, newline-check
make licenses-check       # Verify Apache 2.0 headers
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

- **JUnit 5** + **Mockito**
- API key for tests: `export AI_DASHSCOPE_API_KEY=your-key`
- Run specific test: `./mvnw -pl :<module> -Dtest=<TestClass> test`

## Common Gotchas

1. When using `ShellTool`, always add `ShellToolAgentHook` to manage session lifecycle
2. Dependencies are managed via `spring-ai-alibaba-bom`—check parent pom for versions
3. Graph constants: `StateGraph.START`, `StateGraph.END`, `StateGraph.ERROR`
4. State keys use strategies: `AppendStrategy`, `ReplaceStrategy` in `com.alibaba.cloud.ai.graph.state.strategy`
