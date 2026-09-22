# agent-foundation - 独立 Agent 框架包设计（GenChat-X 集成）

> 本文保留框架提取的历史设计方案，示例 API、里程碑和能力描述不等同于当前实现。业务应用现已独立为 GenChat-X，框架仍是独立的 agent-foundation 项目。当前应用使用 `../agent-foundation` composite build 的 BOM + core，Starter 是可选集成方式；以构建文件及框架实际文档为准。详见 [项目独立说明](../docs/project-independence.md)。

**版本**: 2.0  
**定位**: 企业级、可复用的 AI Agent 执行框架  
**发布形式**: Maven 独立包 + Spring Boot Starter  
**对标**: Spring AI AgentX 1.0.1（成熟设计参考）

---

## 一、框架定位与核心设计

### 1.1 设计理念

**"不造新范式，只做通用引擎"**

```
目标：提供一个干净的、无业务逻辑污染的 Agent 执行框架
特点：
• 不引入 Graph 抽象（复杂度高、学习陡峭）
• 采用 Reactor 驱动的流式执行模型（简洁、高效）
• 完全剥离业务代码（纯框架代码）
• 支持插拔式扩展（Tool/Hook/Strategy）
• 开箱即用（预置必要能力）
```

### 1.2 框架分层（完整架构）

```
┌─────────────────────────────────────────────────────────┐
│           应用层（业务代码）                              │
│    Agent实现 + 业务工具 + 业务逻辑                       │
│    [完全独立，与框架无耦合]                              │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│        agent-foundation - Agent 框架包                   │
├─────────────────────────────────────────────────────────┤
│  核心模块（高内聚）                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ ReactAgent   │  │ AgentLoop    │  │ ToolExecutor │  │
│  │ (30行编排)   │  │ Executor     │  │ (工具权管理) │  │
│  │              │  │ (ReAct循环)  │  │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                         │
│  持久化模块（可选+可配）                                │
│  ┌─────────────────┐  ┌──────────────┐                │
│  │SessionMemory    │  │TraceManager  │                │
│  │Manager(三态)    │  │(调用追踪)    │                │
│  └─────────────────┘  └──────────────┘                │
│                                                         │
│  优化模块（可选+可配）                                  │
│  ┌─────────────────┐  ┌──────────────┐                │
│  │ContextCompression│ │LongTermMemory│                │
│  │Chain(6层)       │  │Manager(LTM)  │                │
│  └─────────────────┘  └──────────────┘                │
│                                                         │
│  生命周期模块（必有）                                  │
│  ┌─────────────────┐  ┌──────────────┐                │
│  │AgentHookManager │  │PauseAdvisor  │                │
│  │(7个事件)        │  │(HITL管理)    │                │
│  └─────────────────┘  └──────────────┘                │
│                                                         │
│  流处理模块（必有）                                    │
│  ┌──────────────────────────────────┐                │
│  │AgentStreamLifecycle              │                │
│  │(流生命周期+背压支持)              │                │
│  └──────────────────────────────────┘                │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│          基础设施层（可选集成）                           │
│  • MySQL/PostgreSQL（会话存储）                         │
│  • PgVector/LanceDB（向量存储）                         │
│  • Redis（分布式协调）                                  │
│  • Spring AI ChatModel（LLM调用）                       │
│  • 其他可选存储后端                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 二、框架包结构设计

### 2.1 Maven 包结构

```
agent-foundation/
├─ agent-foundation-core/            # 核心框架包
│  ├─ pom.xml
│  └─ src/main/java/com/agent/foundation/
│     ├─ core/                       # 核心引擎
│     │  ├─ ReactAgent.java          # 主控编排
│     │  ├─ AgentLoopExecutor.java   # ReAct循环驱动
│     │  ├─ AgentExecutionContext.java
│     │  ├─ ToolExecutor.java        # 工具执行
│     │  └─ AgentStreamLifecycle.java
│     │
│     ├─ hook/                       # 生命周期事件
│     │  ├─ HookEvent.java
│     │  ├─ AgentHook.java
│     │  ├─ BeforeCallEvent.java
│     │  ├─ AfterCallEvent.java
│     │  └─ ... (其他5个事件)
│     │
│     ├─ tool/                       # 工具管理
│     │  ├─ ToolResult.java
│     │  ├─ ToolRegistry.java
│     │  ├─ ToolCallback.java
│     │  └─ PauseAdvisor.java
│     │
│     ├─ memory/                     # 记忆管理
│     │  ├─ ChatMemoryStrategy.java
│     │  ├─ SessionMemoryManager.java
│     │  ├─ MessageWindowMemory.java
│     │  └─ LongTermMemoryConfig.java
│     │
│     ├─ compression/                # 上下文压缩
│     │  ├─ ContextCompressionChain.java
│     │  ├─ ContextPolicy.java
│     │  ├─ L1CompactionStrategy.java
│     │  ├─ ... (L2-L6 策略)
│     │  └─ TokenEstimator.java
│     │
│     ├─ persistence/                # 可选：持久化
│     │  ├─ SessionPersistence.java
│     │  ├─ TracePersistence.java
│     │  └─ PauseStatePersistence.java
│     │
│     ├─ model/                      # 数据模型
│     │  ├─ AgentRequest.java
│     │  ├─ AgentResult.java
│     │  ├─ Message.java
│     │  └─ PauseState.java
│     │
│     └─ config/                     # 配置
│        ├─ FoundationProperties.java
│        └─ FoundationAutoConfiguration.java
│
├─ agent-foundation-spring-boot-starter/    # Spring Boot Starter
│  ├─ pom.xml
│  └─ src/main/resources/
│     └─ META-INF/spring/
│        └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└─ pom.xml (BOM - Bill of Materials)
```

### 2.2 核心类清单

| 类名 | 行数 | 职责 | 可选 |
|------|------|------|------|
| **ReactAgent** | 30 | 主控编排、统一入口 | ❌ |
| **AgentLoopExecutor** | 200 | ReAct循环驱动（Flux） | ❌ |
| **ToolExecutor** | 150 | 工具执行、权管理 | ❌ |
| **AgentStreamLifecycle** | 100 | 流生命周期、背压 | ❌ |
| **AgentHookManager** | 150 | Hook事件分发、优先级 | ❌ |
| **PauseAdvisor** | 100 | HITL管理、两类区分 | ❌ |
| **SessionMemoryManager** | 200 | 三态会话管理 | ✅ |
| **ContextCompressionChain** | 300 | 6层压缩链 | ✅ |
| **LongTermMemoryManager** | 150 | 向量存储、跨会话 | ✅ |
| **TraceManager** | 100 | 调用追踪 | ✅ |

**合计**：核心框架 **1330 行**（不含业务代码）

---

## 三、完整API设计

### 3.1 主入口 ReactAgent

```java
/**
 * 主控 Agent，无业务逻辑，纯框架编排
 */
public class ReactAgent {
    
    // 同步调用
    public AgentResult callForResult(
        String question,
        RunnableParams params
    );
    
    // 流式调用（推荐）
    public Flux<AgentStreamEvent> streamForEvents(
        String question,
        RunnableParams params
    );
    
    // 中断执行
    public void interrupt(String conversationId, String reason);
    
    // 恢复中断
    public Flux<AgentStreamEvent> resumeStream(
        PauseState state,
        Map<String, String> userInputs
    );
}

// 构建 ReactAgent（Builder 模式）
ReactAgent agent = ReactAgent.builder()
    .chatModel(chatModel)                    // 必需
    .toolExecutor(toolExecutor)              // 必需
    .maxRounds(10)                           // 必需
    .contextPolicy(policy)                   // 可选
    .hooks(List.of(auditHook))              // 可选
    .sessionMemoryManager(memoryManager)    // 可选
    .contextCompressionChain(compression)   // 可选
    .longTermMemoryManager(ltmManager)      // 可选
    .build();

// 使用
Flux<AgentStreamEvent> stream = agent.streamForEvents(
    "用户问题",
    RunnableParams.builder()
        .conversationId("conv_123")
        .userId("user_456")
        .addParam("language", "zh-CN")
        .build()
);
```

### 3.2 工具管理 ToolExecutor

```java
/**
 * 工具执行框架，所有工具调用权在这里
 */
public interface ToolExecutor {
    
    // 执行工具
    ToolResult execute(String toolName, String arguments);
    
    // 工具是否需要审批
    boolean requiresApproval(String toolName);
    
    // 注册工具
    void register(String toolName, ToolCallback callback);
    
    // 获取所有工具列表
    List<ToolMetadata> getAvailableTools();
}

// 实现示例
public class MyToolExecutor implements ToolExecutor {
    
    private Map<String, ToolCallback> tools = new HashMap<>();
    private Set<String> approvalTools = new HashSet<>();
    
    @Override
    public ToolResult execute(String toolName, String arguments) {
        ToolCallback tool = tools.get(toolName);
        if (tool == null) throw new ToolNotFoundException(toolName);
        
        try {
            String result = tool.execute(arguments);
            return new ToolResult(toolName, result, true);
        } catch (Exception e) {
            return new ToolResult(toolName, e.getMessage(), false);
        }
    }
    
    @Override
    public boolean requiresApproval(String toolName) {
        return approvalTools.contains(toolName);
    }
}
```

### 3.3 Hook 生命周期

```java
/**
 * 7个标准事件 + 优先级机制
 */
public sealed interface HookEvent permits
    BeforeCallEvent,          // 调用前
    BeforeReasoningEvent,     // LLM推理前
    BeforeToolExecutionEvent, // 工具执行前
    AfterToolExecutionEvent,  // 工具执行后
    AfterReasoningEvent,      // LLM生成后
    AfterCallEvent,           // 调用完成后
    ErrorEvent                // 异常发生

/**
 * Hook 接口，所有扩展点的统一入口
 */
public interface AgentHook {
    HookEvent onEvent(HookEvent event);
    
    // 优先级（降序执行）
    default int priority() { return 50; }
}

// 实现示例
public class AuditHook implements AgentHook {
    @Override
    public HookEvent onEvent(HookEvent event) {
        return switch (event) {
            case BeforeCallEvent e -> {
                log.info("Agent call: {}", e.getQuery());
                yield e;
            }
            case BeforeToolExecutionEvent e -> {
                // 修改工具参数、权限检查等
                e.setArguments(validateAndOverride(e.getArguments()));
                yield e;
            }
            case ErrorEvent e -> {
                // 只读观测，记录错误
                log.error("Agent error: {}", e.getException());
                yield e;
            }
            default -> event;
        };
    }
    
    @Override
    public int priority() { return 100; }  // 最高优先级
}
```

### 3.4 会话管理（三态）

```java
/**
 * 三态会话管理，支持无限长对话
 */
public interface SessionMemoryManager {
    
    // 加载会话（优先级：working > original）
    List<Message> loadSessionMessages(String conversationId);
    
    // 保存会话（三态）
    void saveSession(
        String conversationId,
        SessionState original,
        SessionState working,
        SessionState offload
    );
    
    // 恢复压缩原文
    String reloadContext(String uuid);
}

public record SessionState(
    List<Message> messages,
    Map<String, String> metadata
) {}

// 使用示例
SessionMemoryManager memory = SessionMemoryManager.builder()
    .dataSource(dataSource)
    .enableOriginalMessages(true)   // 真相链路
    .enableWorkingMessages(true)    // 工作视图
    .enableOffloadContext(true)     // 压缩移出
    .build();
```

### 3.5 上下文压缩（6层）

```java
/**
 * 6层渐进式压缩链
 * L1-L3: 规则压缩（无LLM）
 * L4-L6: 智能压缩（需LLM）
 */
public interface ContextCompressionChain {
    
    // 执行压缩
    void compress(List<Message> messages, ContextPolicy policy);
    
    // 判断是否需要压缩
    boolean shouldCompress(List<Message> messages, ContextPolicy policy);
}

// 配置策略
ContextPolicy policy = ContextPolicy.builder()
    .msgThreshold(100)              // 消息数门禁
    .tokenThreshold(90000)          // Token数门禁
    .lastKeep(50)                   // 保护区（最近50条）
    .minConsecutiveToolMessages(6)  // L1触发条件
    .largePayloadTokens(2000)       // L2/L3大消息阈值
    .build();

// 使用
ContextCompressionChain compression = 
    new DefaultContextCompressionChain(chatModel);

compression.compress(messages, policy);
// messages 被原地修改，offload_context 自动存储
```

### 3.6 HITL 两类区分

```java
/**
 * PauseAdvisor：人机交互管理
 */
public class PauseAdvisor {
    
    // 审批类：工具执行需人工批准
    public static PauseEvent checkApproval(String toolName) {
        if (requiresApproval(toolName)) {
            return new PauseEvent(
                PauseReason.HITL_TOOL_REQUEST,
                toolName
            );
        }
        return null;
    }
    
    // 输入类：模型需要人工输入（ask_user 工具）
    // 框架自动生成 ask_user 工具，模型主动调用时暂停
}

public enum PauseReason {
    HITL_TOOL_REQUEST,    // 工具审批
    WAITING_FOR_INPUT,    // 等待用户输入
    USER_INTERRUPT        // 用户中断
}
```

---

## 四、可选功能模块

### 4.1 长期记忆（跨会话）

```java
/**
 * 可选：跨会话向量存储管理
 */
public interface LongTermMemoryManager {
    
    // 异步从会话中抽取记忆
    void extractAndStore(String conversationId);
    
    // 检索相关记忆
    List<String> retrieveRelevant(String userId, String query, int topK);
    
    // 配置
    LongTermMemoryConfig getConfig();
}

// 使用
LongTermMemoryManager ltm = LongTermMemoryManager.builder()
    .vectorStore(vectorStore)
    .embeddingModel(embeddingModel)
    .enabled(true)
    .mergeStrategy(MergeStrategy.SIMILARITY_BASED)
    .topK(5)
    .build();

// 在 BeforeReasoningEvent 中注入长期记忆
```

### 4.2 调用追踪

```java
/**
 * 可选：完整的调用链追踪
 */
public interface TraceManager {
    
    // 记录每轮调用
    void recordRound(
        String conversationId,
        int roundNumber,
        List<Message> requests,
        String response,
        List<ToolCall> toolCalls,
        long tokens
    );
    
    // 记录思考内容（o1/deepseek/claude）
    void recordThinking(String conversationId, int roundNumber, String thinking);
    
    // 查询调用历史
    List<TraceRecord> queryHistory(String conversationId);
}
```

---

## 五、使用场景示例

### 5.1 最小化应用

```java
@Configuration
public class MinimalAgentConfig {
    
    @Bean
    public ReactAgent agent(
        ChatModel chatModel,
        ToolExecutor toolExecutor
    ) {
        return ReactAgent.builder()
            .chatModel(chatModel)
            .toolExecutor(toolExecutor)
            .maxRounds(10)
            .build();
    }
}

@RestController
public class ChatController {
    
    @GetMapping("/chat/stream")
    public Flux<AgentStreamEvent> chat(
        @RequestParam String question,
        @RequestParam String userId
    ) {
        RunnableParams params = RunnableParams.builder()
            .conversationId(UUID.randomUUID().toString())
            .userId(userId)
            .build();
        
        return agent.streamForEvents(question, params);
    }
}
```

### 5.2 完整功能应用

```java
@Configuration
public class FullFeatureAgentConfig {
    
    @Bean
    public ReactAgent agent(
        ChatModel chatModel,
        ToolExecutor toolExecutor,
        SessionMemoryManager memory,
        ContextCompressionChain compression,
        LongTermMemoryManager ltm,
        TraceManager trace,
        AgentHook... hooks
    ) {
        return ReactAgent.builder()
            .chatModel(chatModel)
            .toolExecutor(toolExecutor)
            .maxRounds(10)
            .contextPolicy(ContextPolicy.defaults())
            .sessionMemoryManager(memory)
            .contextCompressionChain(compression)
            .longTermMemoryManager(ltm)
            .traceManager(trace)
            .hooks(Arrays.asList(hooks))
            .build();
    }
}
```

### 5.3 业务定制

```java
// 完全独立的业务 Agent，基于框架
public class CustomBusinessAgent {
    
    private final ReactAgent framework;
    private final BusinessService service;
    
    public Flux<AgentStreamEvent> handleBusinessRequest(String request) {
        // 业务逻辑前处理
        String enrichedRequest = service.enrich(request);
        
        // 调用框架
        Flux<AgentStreamEvent> result = framework.streamForEvents(
            enrichedRequest,
            RunnableParams.builder()
                .conversationId(generateId())
                .userId(getCurrentUser())
                .addParam("domain", "business")
                .build()
        );
        
        // 业务逻辑后处理
        return result.doFinally(signal -> service.recordSignal(signal));
    }
}
```

---

## 六、完全剥离业务代码

### 6.1 框架代码 vs 业务代码的明确边界

| 层级 | 框架代码 | 业务代码 | 示例 |
|------|---------|--------|------|
| **Agent** | ReactAgent（编排） | ChatAgent/FileAgent（实现） | ✅ 彻底分离 |
| **Tool** | ToolExecutor（权管理） | FileSearchTool/WebSearchTool（实现） | ✅ 彻底分离 |
| **Memory** | SessionMemoryManager（三态） | 业务表设计（可选） | ✅ 接口清晰 |
| **Hook** | AgentHookManager（分发） | AuditHook/BusinessLogicHook（实现） | ✅ 彻底分离 |
| **Stream** | AgentStreamLifecycle（生命周期） | 业务事件处理（可选） | ✅ 完全正交 |

### 6.2 架构约束

**框架包 (agent-foundation) 中禁止包含**：
- ❌ 任何业务逻辑
- ❌ 任何业务模型（如 ChatSession、FileInfo 等）
- ❌ 任何业务表设计
- ❌ 任何特定领域的工具（只提供框架）
- ❌ 任何 Controller（只提供 API）

**业务包（GenChat-X，文档代称 genchat-x-app）中包含**：
- ✅ 具体的 Agent 实现
- ✅ 业务工具集
- ✅ 业务表设计
- ✅ 业务 Hook 实现
- ✅ REST Controller
- ✅ 业务特定的优化

---

## 七、发布与依赖形式

### 7.1 Maven 发布

```xml
<!-- 业务项目依赖框架 -->
<dependency>
    <groupId>com.agent.foundation</groupId>
    <artifactId>agent-foundation-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>

<!-- 或直接依赖核心包 -->
<dependency>
    <groupId>com.agent.foundation</groupId>
    <artifactId>agent-foundation-core</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### 7.2 自动配置（Spring Boot Starter）

```java
// agent-foundation-spring-boot-starter 提供
@Configuration
@EnableConfigurationProperties(FoundationProperties.class)
public class FoundationAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ReactAgent reactAgent(...) { }
    
    @Bean
    @ConditionalOnProperty("foundation.session.enabled", havingValue = "true")
    public SessionMemoryManager sessionMemoryManager(...) { }
    
    @Bean
    @ConditionalOnProperty("foundation.compression.enabled", havingValue = "true")
    public ContextCompressionChain compression(...) { }
    
    // ... 其他可选 Bean
}
```

### 7.3 配置项

```yaml
# application.yml
foundation:
  agent:
    max-rounds: 10
    max-retries: 2
  
  # 可选
  session:
    enabled: true
    datasource: primary
  
  compression:
    enabled: true
    msg-threshold: 100
    token-threshold: 90000
    last-keep: 50
  
  ltm:
    enabled: true
    vector-store: pgvector
  
  trace:
    enabled: true
    persist: true
```

---

## 八、与 GenChat-X 的集成

### 8.1 GenChat-X 依赖关系（Starter 集成方案）

```
genchat-x-app (业务应用，文档代称)
├─ ChatAgent / FileAgent / ...
├─ ChatController / FileController / ...
├─ 业务表设计 / 业务 Hook / ...
└─ 依赖
   └─ agent-foundation-spring-boot-starter
      └─ 核心框架包（无业务）
```

### 8.2 迁移路径

```
Phase 1-2: 框架重构
  原 GenChat 内部代码 → agent-foundation 包（历史提取过程）
  
Phase 3: 抽离发布
  agent-foundation 作为独立 Maven 包
  
Phase 4: 使用外部包
  genchat-x-app 依赖 agent-foundation (Maven)
  
Phase 5: 开源发布
  agent-foundation 独立开源（GitHub）
```

---

## 九、与 LangGraph 的对比

| 维度 | LangGraph | agent-foundation |
|------|-----------|-------------------|
| **抽象** | Graph（有向图）| Stream（反应式流） |
| **学习曲线** | 陡（需理解图论） | 平缓（Reactor即可） |
| **灵活性** | 高（任意拓扑） | 中等（ReAct为主） |
| **生产就绪** | ✅ 成熟 | ✅ 成熟 |
| **流式支持** | ⚠️ 有限 | ✅ 完整 |
| **长会话支持** | ❌ 无 | ✅ 6层压缩 |
| **中断恢复** | ❌ 无 | ✅ 精细 |
| **HITL机制** | 基础 | ✅ 两类区分 |
| **长期记忆** | ✅ 支持 | ✅ 支持 |
| **代码污染** | 中等 | ❌ 零污染 |

---

## 十、核心优势总结

✅ **干净的框架** - 1330行纯框架代码，零业务污染  
✅ **完全的可复用性** - 可独立发布、在任何项目中使用  
✅ **生产级设计** - 基于 AgentX 验证的架构  
✅ **简洁的编程模型** - 无需学习 Graph，只需 Reactor  
✅ **强大的功能** - 三态、6层压缩、HITL、长期记忆  
✅ **灵活的扩展** - Hook/Tool/Strategy 全部可定制  
✅ **零耦合集成** - 像导入 Spring Data、Spring Security 一样简单  

---

## 立即行动

### Week 1-2: 代码提取
将原 GenChat 中的框架代码提取到 `agent-foundation` Maven 包（历史计划）

### Week 3-4: 测试与完善
单元测试、集成测试、性能基准

### Week 5: 发布
发布到 Maven Central 或内部 Nexus

### 后续: 在其他项目中复用
```xml
<!-- 任何其他 AI 应用都可以直接依赖使用 -->
<dependency>
    <groupId>com.agent.foundation</groupId>
    <artifactId>agent-foundation-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

---

**目标**：构建一个企业级、可复用的 AI Agent 框架，像 Spring Data、Spring Security 一样被广泛使用。


