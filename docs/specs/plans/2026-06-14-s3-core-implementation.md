# S3: harness-core Agent 运行时实现计划

> **目标**: 实现 Agent 状态机、双层 AgentLoop、Steering/FollowUp 队列、ToolHook 管线、AgentLifecycleEvent

---

## 文件结构

```
harness-core/src/main/java/io/github/frank/harness/core/
├── agent/
│   ├── AgentState.java
│   └── Agent.java
├── loop/
│   └── AgentLoop.java
├── event/
│   └── AgentLifecycleEvent.java
├── queue/
│   ├── SteeringQueue.java
│   └── FollowUpQueue.java
├── hook/
│   ├── ToolHook.java
│   └── HookChain.java
└── memory/
    ├── MemoryStore.java
    ├── ContextWindow.java
    └── CompactionStrategy.java
```

---

### 任务 1：AgentState + AgentLifecycleEvent + 队列

- [ ] 创建所有基础类型并编译验证

```java
// AgentState.java
package io.github.frank.harness.core.agent;
public enum AgentState { IDLE, RUNNING, STOPPING, STOPPED }

// AgentLifecycleEvent.java
package io.github.frank.harness.core.event;
import io.github.frank.harness.ai.protocol.*;
public sealed interface AgentLifecycleEvent
        permits AgentLifecycleEvent.LoopStarted, AgentLifecycleEvent.TextDelta,
                AgentLifecycleEvent.ToolCallStart, AgentLifecycleEvent.ToolCallEnd,
                AgentLifecycleEvent.TurnComplete, AgentLifecycleEvent.LoopComplete,
                AgentLifecycleEvent.Error {
    record LoopStarted() implements AgentLifecycleEvent {}
    record TextDelta(String text) implements AgentLifecycleEvent {}
    record ToolCallStart(Content.ToolCallContent call) implements AgentLifecycleEvent {}
    record ToolCallEnd(Content.ToolCallContent call, ToolResult result) implements AgentLifecycleEvent {}
    record TurnComplete(Message assistantMessage) implements AgentLifecycleEvent {}
    record LoopComplete(String stopReason) implements AgentLifecycleEvent {}
    record Error(String message, Throwable cause) implements AgentLifecycleEvent {}
}

// SteeringQueue.java
package io.github.frank.harness.core.queue;
import java.util.concurrent.ConcurrentLinkedQueue;
public class SteeringQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    public void push(String text) { queue.add(text); }
    public String drain() { return String.join("\n", queue); }
    public boolean isEmpty() { return queue.isEmpty(); }
}

// FollowUpQueue.java
package io.github.frank.harness.core.queue;
import java.util.concurrent.ConcurrentLinkedQueue;
public class FollowUpQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    public void push(String text) { queue.add(text); }
    public String poll() { return queue.poll(); }
    public boolean isEmpty() { return queue.isEmpty(); }
}
```

- [ ] Commit: `feat(core): add AgentState, AgentLifecycleEvent, SteeringQueue, FollowUpQueue`

---

### 任务 2：ToolHook + HookChain

```java
// ToolHook.java
package io.github.frank.harness.core.hook;
import io.github.frank.harness.ai.protocol.*;
import com.fasterxml.jackson.databind.JsonNode;
public interface ToolHook {
    boolean beforeToolCall(Content.ToolCallContent call, JsonNode args);
    ToolResult afterToolCall(Content.ToolCallContent call, ToolResult result);
}

// HookChain.java
package io.github.frank.harness.core.hook;
import java.util.List;
public class HookChain {
    private final List<ToolHook> hooks;
    public HookChain(List<ToolHook> hooks) { this.hooks = List.copyOf(hooks); }
    public boolean runBefore(Content.ToolCallContent call, JsonNode args) {
        for (var hook : hooks) { if (!hook.beforeToolCall(call, args)) return false; }
        return true;
    }
    public ToolResult runAfter(Content.ToolCallContent call, ToolResult result) {
        ToolResult r = result;
        for (var hook : hooks) { r = hook.afterToolCall(call, r); }
        return r;
    }
}
```

- [ ] Commit: `feat(core): add ToolHook + HookChain`

---

### 任务 3：memory/ 接口（3 个接口文件）

```java
// MemoryStore, ContextWindow, CompactionStrategy
```
留空实现，只定义接口签名。

- [ ] Commit: `feat(core): add memory interfaces — MemoryStore, ContextWindow, CompactionStrategy`

---

### 任务 4：Agent 类 + AgentLoop（核心）

Agent 类持有状态、上下文、工具、队列、hooks。AgentLoop 实现双层循环 + 工具执行管线。

- [ ] 创建 Agent.java 和 AgentLoop.java
- [ ] 全量编译验证
- [ ] Commit: `feat(core): add Agent + AgentLoop — double-loop with tool pipeline`
