# Agent Harness 框架设计规格

> Java 21 实现的 AI Agent Harness 框架，参考 Pi Agent 三层架构，面向 Coding Agent + Memory/Context 深度探索

## 一、项目定位

这是一个**学习型框架**——目标不是做出另一个 LangChain4j，而是：

1. **理解 Harness 设计**：通过亲手实现三层架构理解 Agent 框架的内核
2. **垂直领域探索**：以 Coding Agent 为起点，在 Memory/Context 方向深钻
3. **实践 Spec 编程**：把 Spec 作为可验证的约束层，而非文档

### 技术选型

| 维度 | 选择 | 理由 |
|------|------|------|
| 语言 | Java 21 | Virtual Threads + `sealed` class + 生态成熟 |
| 构建 | Maven 3.9+ | 多模块管理成熟 |
| HTTP | OkHttp 4.x | 纯协议适配层，无框架依赖 |
| JSON | Jackson 2.x | Java 生态标准 |
| 测试 | JUnit 5 + AssertJ + WireMock | 分层测试 + HTTP Mock |
| 模型适配 | 纯 HTTP（核心）+ LangChain4j（可选扩展） | 先理解原理，后提供便利 |

### 不做什么

- 不做 Web UI（CLI 即可）
- 不做多 Agent 编排
- 不做 Python SDK
- 不追求生产级性能优化

---

## 二、模块架构

### 2.1 项目骨架

```
/Users/sunhanbin/Documents/workspace/agent-harness/
├── pom.xml                          # 父 POM，groupId: io.github.frank
├── harness-bom/                     # BOM 统一依赖版本
├── harness-ai-core/                 # 模型适配层（纯 HTTP）
├── harness-ai-langchain4j/          # LangChain4j 适配器（可选扩展）
├── harness-core/                    # Agent 运行时
├── harness-coding/                  # Coding Agent 产品层
└── docs/specs/                      # Spec 设计文档
```

### 2.2 依赖方向（不可逆）

```
harness-coding → harness-core → harness-ai-core
                    ↘ (可选)
               harness-ai-langchain4j
```

`harness-ai-langchain4j` 通过 SPI 运行时注入，编译期不硬依赖。

### 2.3 Maven 坐标

| 模块 | groupId | artifactId |
|------|---------|-----------|
| 父 POM | `io.github.frank` | `agent-harness` |
| BOM | `io.github.frank` | `harness-bom` |
| AI 核心 | `io.github.frank` | `harness-ai-core` |
| AI 扩展 | `io.github.frank` | `harness-ai-langchain4j` |
| 运行时 | `io.github.frank` | `harness-core` |
| 产品层 | `io.github.frank` | `harness-coding` |

### 2.4 编译期隔离规则

- `harness-coding` 只能 import `harness-core` 公开 API
- `harness-coding` 不能 import `harness-ai-core` 的具体 Provider 实现
- Provider 通过 SPI `ServiceLoader` 在运行时注入
- 用 ArchUnit 在 CI 中 enforce 这些规则

---

## 三、harness-ai-core 协议抽象

### 3.1 Message 协议

```java
public sealed interface Content permits 
    TextContent, ThinkContent, ToolCallContent, ToolResultContent {}

public record Message(
    Role role,                      // SYSTEM | USER | ASSISTANT | TOOL
    List<Content> contents,         // 有序内容块
    String stopReason,              // end_turn | max_tokens | tool_use | error
    Map<String, Object> metadata    // usage/timing 等 provider 特定信息
) {}
```

### 3.2 Tool 定义

```java
public record Tool(
    String name,
    String description,
    JsonSchema parameters,          // JSON Schema 参数定义
    Function<JsonNode, ToolResult> execute  // 执行体
) {}
```

### 3.3 AssistantEvent（流式事件）

```java
public sealed interface AssistantEvent {
    record TextDelta(int index, String text) implements AssistantEvent {}
    record ThinkDelta(int index, String reasoning) implements AssistantEvent {}
    record ToolCallStart(String id, String name) implements AssistantEvent {}
    record ToolCallArgs(String id, String delta) implements AssistantEvent {}
    record ToolCallDone(String id, String name, String arguments) implements AssistantEvent {}
    record Done(String stopReason, Map<String, Object> usage) implements AssistantEvent {}
}
```

### 3.4 Provider 接口

```java
public interface ModelProvider {
    String name();
    Publisher<AssistantEvent> stream(List<Message> context, List<Tool> tools, ModelConfig config);
    CompletableFuture<Message> complete(List<Message> context, List<Tool> tools, ModelConfig config);
}
```

实现：`OpenAIProvider`、`AnthropicProvider`——纯 OkHttp + SSE 解析。

---

## 四、harness-core Agent 运行时

### 4.1 Agent 状态机

```
IDLE → RUNNING → STOPPING → STOPPED
```

`state` 用 `AtomicReference` 保证线程安全。

### 4.2 AgentLoop 双层循环

**外层**：处理 follow-up 队列（"做完以后再做"）
**内层**：处理 steering 队列 + 工具调用循环

```
outerLoop:
  while (!STOPPED) {
    innerLoop()
    if (followUp 有消息) → 注入下轮 → 继续
    else → 结束
  }

innerLoop:
  while (RUNNING) {
    drainSteering()        // 消费转向消息
    streamAssistant()      // 请求模型
    if (无工具调用) → 返回
    executeToolsConcurrently()  // Virtual Thread 并发
  }
```

### 4.3 工具执行管线

```
Prepare → BeforeHook → Execute → AfterHook → Terminate 检查
```

- 并发执行，但结果按原始调用顺序写回 context
- terminate 规则：只有当前批次全部工具都设置 terminate 才提前停止
- 任何一步失败生成错误 toolResult，不崩溃 loop

### 4.4 hook 接口

```java
public interface ToolHook {
    boolean beforeToolCall(ToolCallContent call, JsonNode args);
    ToolResult afterToolCall(ToolCallContent call, ToolResult result);
}
```

### 4.5 memory/ 预留接口

```java
public interface MemoryStore {
    void save(String key, String value);
    Optional<String> load(String key);
    List<String> search(String query, int limit);
}

public interface ContextWindow {
    List<Message> fit(List<Message> fullHistory, int maxTokens);
}

public interface CompactionStrategy {
    String name();
    List<Message> compact(List<Message> history, int targetTokens);
}
```

---

## 五、harness-coding 产品层

### 5.1 目录结构

```
harness-coding/src/main/java/io/github/frank/harness/coding/
├── session/      AgentSession + JsonlSessionStore + SessionManager
├── tool/         ReadTool / WriteTool / BashTool / GrepTool / FindTool / LsTool / PatchTool + ToolRegistry
├── resource/     ResourceLoader / SkillResolver / ProjectContext
├── compaction/   CompactionService + 三种策略实现
└── cli/          HarnessCLI + InteractiveLoop
```

### 5.2 内置工具

| 工具 | 用途 |
|------|------|
| read | 读文件 |
| write | 写/覆写文件 |
| bash | 执行 shell 命令 |
| grep | 正则搜索文件内容 |
| find | glob 查找文件 |
| ls | 列目录 |
| patch | 精确文本编辑 |

### 5.3 会话持久化

JSONL 格式，一行一条消息，追加写入无需锁。
支持会话树分支（`parentId` 指针）。

### 5.4 资源加载优先级

```
全局默认规则 → 项目规则(AGENTS.md/CLAUDE.md) → 技能 → 用户自定义 Prompt
```

后加载覆盖前加载。

### 5.5 配置文件

```yaml
# {project}/.harness/config.yaml
model:
  provider: openai
  model: gpt-4o
  temperature: 0.1
  maxTokens: 8192

permissions:
  allowedPaths: [src/**, pom.xml]
  blockedPaths: [.git/**, **/*.key, **/.env]
  requireApprovalFor: [git push, rm -rf, curl]

memory:
  compactionStrategy: sliding-window
  maxContextTokens: 64000
  compactionThreshold: 0.8

specs:
  enforceModuleBoundaries: true
  validateToolSchemas: true
```

---

## 六、Spec 编程集成

### 6.1 Spec 目录结构

```
{project}/.harness/specs/
├── ARCHITECTURE.md              # 项目级 Spec
├── adr/                         # 架构决策记录
├── features/                    # 功能 Spec（OpenSpec 风格）
│   └── FEAT-001-xxx/
│       ├── spec.md
│       ├── contract.json
│       └── test-scenarios.yaml
└── changes/                     # 进行中的变更
    └── CHG-001-xxx/
        ├── proposal.md
        └── impact.json
```

### 6.2 Spec 注入 AI

SpecLoader 将 ARCHITECTURE.md 的模块边界、API 契约、错误模型注入 System Prompt 的 SPEC CONSTRAINTS 区块。

### 6.3 CI Gate

- ArchUnit 检查模块边界
- JSON Schema 校验工具参数
- OpenAPI diff 检测接口契约变更

---

## 七、Memory 探索路线图

| Phase | 内容 | 产物 |
|-------|------|------|
| 1 | 基础记忆存储 | FileSystemMemoryStore + ExactTokenCounter |
| 2 | 上下文压缩策略 | SlidingWindow / Summarize / Hybrid + Benchmark |
| 3 | 结构化记忆（Semantic Memory） | VectorMemoryStore（向量检索） |
| 4 | 跨会话记忆（Long-term Memory） | CrossSessionMemory + MemoryInjectionPolicy |
| 5 | 记忆即 Spec | MemoryPolicy Spec + 合规性测试 |

---

## 八、测试策略

### 8.1 测试分层

```
harness-ai-core 单元测试
  └─ WireMock HTTP Server Mock → Provider 正确性

harness-core 核心测试（★ 最重要）
  └─ Mock ModelProvider → AgentLoop 所有控制流分支

harness-coding 集成测试
  └─ 完整 prompt → response 链路

Spec 验证测试（CI 自动化）
  └─ ArchUnit + JSON Schema + OpenAPI diff
```

### 8.2 关键测试桩

`MockProvider`：预设队列响应（文本回复 / 工具调用 / 错误），让 AgentLoop 完全可控可测。

### 8.3 AgentLoop 必测场景

1. 无工具调用 → 直接返回 assistant message
2. 单个工具调用 → 执行后模型再回复
3. 多个工具调用 → 并发执行，顺序写回
4. steering 中途转向 → 下轮模型请求前注入
5. followUp 追加任务 → 外层循环二次进入
6. abort 中止 → 工具执行被中断
7. tool prepare 失败 → 错误 toolResult 不崩溃
8. terminate 规则 → 所有工具都 terminate 才停止

---

## 九、实现阶段

| 阶段 | 内容 | 预计模块 |
|------|------|---------|
| S1 | 项目骨架搭建 | 父 POM + 5 个模块 + .gitignore |
| S2 | harness-ai-core 协议 + OpenAI Provider | Message/Tool/Event + streamSimple |
| S3 | harness-core Agent 运行时 | Agent + AgentLoop + MockProvider 测试 |
| S4 | harness-coding 工具 + 会话 | 7 个工具 + JsonlSessionStore + CLI |
| S5 | 端到端集成 + 真实模型验证 | HarnessCLI 启动 → 真实对话 |
| S6 | Memory Phase 1 + Spec 集成 | FileSystemMemoryStore + SpecLoader |

---

*设计日期：2026-06-14 | 参考：Pi Agent (cellinlab/how-pi-agent-works)*
