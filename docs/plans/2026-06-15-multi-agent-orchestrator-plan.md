# Multi-Agent Orchestrator 实现计划

> **面向 AI 代理的工作者：** 此计划包含 10 个任务。harness-core 零改动，全部在新模块 `harness-orchestrator` 中。

**目标：** 实现任务分解 → 并行派发 → 结果汇总的 multi-agent Orchestrator

**架构：** 新 Maven 模块 `harness-orchestrator`，依赖 `harness-core` + `harness-ai-core`。不改动现有代码。

**技术栈：** Java 17 + Maven + jackson-databind（已有） + java.util.concurrent

**依赖顺序：**

```
Task 1 (模块搭建 + pom)
  ↓
Task 2 (common 类型: WorkerSpec, WorkerResult, OrchestratorResult)
  ↓
Task 3 (SynthesisStrategy + ConcatSynthesizer + LlmSynthesizer)
  ↓
Task 4 (DecompositionStrategy + SubTask + LlmDecomposer)
  ↓
Task 5 (Orchestrator + OrchestratorConfig)
  ↓
Task 6 (HarnessCLI 集成 /orchestrate 命令)
  ↓
Task 7 (Tests: ConcatSynthesizer + LlmDecomposer)
  ↓
Task 8 (Tests: Orchestrator integration)
  ↓
Task 9 (package-info for router/ + collaborative/ 预留包)
  ↓
Task 10 (全量编译 + 测试验证)
```

---

**文件清单：**

| 文件 | 操作 | 职责 |
|------|------|------|
| `harness-orchestrator/pom.xml` | 创建 | Maven 模块，依赖 harness-core |
| `harness-orchestrator/.../common/WorkerSpec.java` | 创建 | 子 Agent 配置 record |
| `harness-orchestrator/.../common/WorkerResult.java` | 创建 | 子任务结果 record |
| `harness-orchestrator/.../common/OrchestratorResult.java` | 创建 | 汇总结果 record |
| `harness-orchestrator/.../orchestrator/OrchestratorConfig.java` | 创建 | 配置 record |
| `harness-orchestrator/.../orchestrator/Orchestrator.java` | 创建 | 核心编排器 |
| `harness-orchestrator/.../orchestrator/decompose/DecompositionStrategy.java` | 创建 | 分解接口 |
| `harness-orchestrator/.../orchestrator/decompose/SubTask.java` | 创建 | 子任务 record |
| `harness-orchestrator/.../orchestrator/decompose/LlmDecomposer.java` | 创建 | LLM 驱动分解 |
| `harness-orchestrator/.../orchestrator/synthesize/SynthesisStrategy.java` | 创建 | 汇总接口 |
| `harness-orchestrator/.../orchestrator/synthesize/ConcatSynthesizer.java` | 创建 | 简单拼接 |
| `harness-orchestrator/.../orchestrator/synthesize/LlmSynthesizer.java` | 创建 | LLM 汇总 |
| `harness-orchestrator/.../router/package-info.java` | 创建 | Phase 2 预留 |
| `harness-orchestrator/.../collaborative/package-info.java` | 创建 | Phase 3 预留 |
| `harness-coding/.../cli/HarnessCLI.java` | 修改 | 新增 /orchestrate 命令 |
| `pom.xml`（父） | 修改 | 添加 harness-orchestrator 模块 |

---

### 任务 1：模块搭建 + pom.xml

**文件：**
- 创建：`harness-orchestrator/pom.xml`
- 修改：`pom.xml`（父 POM — 添加模块）

- [ ] **步骤 1：创建 harness-orchestrator/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-orchestrator</artifactId>

    <name>Harness Orchestrator</name>
    <description>Multi-agent task decomposition and parallel execution</description>

    <dependencies>
        <dependency>
            <groupId>io.github.frank</groupId>
            <artifactId>harness-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：在父 pom.xml 的 `<modules>` 中添加 harness-orchestrator**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness
```

在 `<module>harness-coding</module>` 之后添加：
```xml
        <module>harness-orchestrator</module>
```

- [ ] **步骤 3：创建基础目录**

```bash
mkdir -p harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/{common,orchestrator/decompose,orchestrator/synthesize,router,collaborative}
mkdir -p harness-orchestrator/src/test/java/io/github/frank/harness/orchestrator
```

- [ ] **步骤 4：验证**

```bash
mvn compile -pl harness-orchestrator -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-orchestrator/pom.xml pom.xml
git commit -m "build: add harness-orchestrator module — multi-agent task decomposition"
```

---

### 任务 2：Common 类型（WorkerSpec, WorkerResult, OrchestratorResult）

**文件：**
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/common/WorkerSpec.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/common/WorkerResult.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/common/OrchestratorResult.java`

- [ ] **步骤 1：创建 WorkerSpec.java**

```java
package io.github.frank.harness.orchestrator.common;

import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.core.sandbox.Sandbox;

import java.util.List;

public record WorkerSpec(
    String role,
    String systemPrompt,
    List<Tool> tools,
    Sandbox sandbox,
    ModelConfig modelOverride
) {
    public WorkerSpec {
        if (tools == null) tools = List.of();
    }

    public static WorkerSpec of(String role, String systemPrompt, List<Tool> tools) {
        return new WorkerSpec(role, systemPrompt, tools, null, null);
    }
}
```

- [ ] **步骤 2：创建 WorkerResult.java**

```java
package io.github.frank.harness.orchestrator.common;

public record WorkerResult(
    String subtaskId,
    boolean success,
    String output,
    int turnsTaken,
    String errorMessage
) {
    public static WorkerResult success(String subtaskId, String output) {
        return new WorkerResult(subtaskId, true, output, 0, null);
    }

    public static WorkerResult timeout(String subtaskId) {
        return new WorkerResult(subtaskId, false, "", 0, "Timed out");
    }

    public static WorkerResult error(String subtaskId, String message) {
        return new WorkerResult(subtaskId, false, "", 0, message);
    }
}
```

- [ ] **步骤 3：创建 OrchestratorResult.java**

```java
package io.github.frank.harness.orchestrator.common;

import java.util.List;

public record OrchestratorResult(
    String summary,
    List<WorkerResult> workerResults,
    boolean allSucceeded
) {
    public static OrchestratorResult of(String summary, List<WorkerResult> results) {
        boolean allOk = results.stream().allMatch(WorkerResult::success);
        return new OrchestratorResult(summary, results, allOk);
    }
}
```

- [ ] **步骤 4：验证**

```bash
mvn compile -pl harness-orchestrator -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/common/
git commit -m "feat: add common types — WorkerSpec, WorkerResult, OrchestratorResult"
```

---

### 任务 3：Synthesis 策略

**文件：**
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/synthesize/SynthesisStrategy.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/synthesize/ConcatSynthesizer.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/synthesize/LlmSynthesizer.java`

- [ ] **步骤 1：创建 SynthesisStrategy.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import java.util.List;

@FunctionalInterface
public interface SynthesisStrategy {
    String synthesize(String goal, List<WorkerResult> results);
}
```

- [ ] **步骤 2：创建 ConcatSynthesizer.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import java.util.List;
import java.util.stream.Collectors;

public class ConcatSynthesizer implements SynthesisStrategy {
    @Override
    public String synthesize(String goal, List<WorkerResult> results) {
        var sb = new StringBuilder();
        sb.append("## Goal: ").append(goal).append("\n\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("### SubTask ").append(i + 1);
            if (!r.success()) {
                sb.append(" [FAILED: ").append(r.errorMessage()).append("]");
            }
            sb.append("\n").append(r.output()).append("\n\n");
        }
        long successCount = results.stream().filter(WorkerResult::success).count();
        sb.append("---\n**").append(successCount).append("/").append(results.size())
            .append("** subtasks succeeded.");
        return sb.toString();
    }
}
```

- [ ] **步骤 3：创建 LlmSynthesizer.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.orchestrator.common.WorkerResult;

import java.util.List;

public class LlmSynthesizer implements SynthesisStrategy {
    private final ModelProvider provider;
    private final ModelConfig config;

    public LlmSynthesizer(ModelProvider provider, ModelConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public String synthesize(String goal, List<WorkerResult> results) {
        if (results.isEmpty()) return "No subtasks executed.";

        var sb = new StringBuilder();
        sb.append("Goal: ").append(goal).append("\n\nSubtask results:\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("--- SubTask ").append(i + 1).append(" ---\n");
            sb.append("Status: ").append(r.success() ? "SUCCESS" : "FAILED");
            if (!r.success()) sb.append(" (").append(r.errorMessage()).append(")");
            sb.append("\n").append(r.output()).append("\n");
        }

        var prompt = Message.user(
            "You are a synthesis expert. Given a goal and subtask results, " +
            "write a concise summary (3-5 sentences) covering what was done, " +
            "key findings, and any remaining work.\n\n" + sb);

        var response = provider.complete(List.of(prompt), List.of(), config).join();
        return extractText(response);
    }

    private String extractText(Message msg) {
        return msg.contents().stream()
            .filter(c -> c instanceof io.github.frank.harness.ai.protocol.Content.TextContent)
            .map(c -> ((io.github.frank.harness.ai.protocol.Content.TextContent) c).text())
            .collect(java.util.stream.Collectors.joining());
    }
}
```

- [ ] **步骤 4：验证**

```bash
mvn compile -pl harness-orchestrator -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/synthesize/
git commit -m "feat: SynthesisStrategy + ConcatSynthesizer + LlmSynthesizer"
```

---

### 任务 4：Decomposition 策略

**文件：**
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/decompose/DecompositionStrategy.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/decompose/SubTask.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/decompose/LlmDecomposer.java`

- [ ] **步骤 1：创建 DecompositionStrategy.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.decompose;

import java.util.List;

@FunctionalInterface
public interface DecompositionStrategy {
    List<SubTask> decompose(String goal);
}
```

- [ ] **步骤 2：创建 SubTask.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.decompose;

import io.github.frank.harness.orchestrator.common.WorkerSpec;
import java.util.List;
import java.util.UUID;

public record SubTask(
    String id,
    String description,
    WorkerSpec spec,
    List<String> dependsOn
) {
    public SubTask {
        if (dependsOn == null) dependsOn = List.of();
    }

    public static SubTask of(String description, WorkerSpec spec) {
        return new SubTask(UUID.randomUUID().toString(), description, spec, List.of());
    }
}
```

- [ ] **步骤 3：创建 LlmDecomposer.java**

```java
package io.github.frank.harness.orchestrator.orchestrator.decompose;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.orchestrator.common.WorkerSpec;

import java.util.List;

public class LlmDecomposer implements DecompositionStrategy {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ModelProvider provider;
    private final ModelConfig config;
    private final WorkerSpec defaultWorkerSpec;

    private static final String PROMPT = """
        Break the goal into 2-5 independent subtasks that can run in parallel.
        Each subtask must be a complete, actionable instruction.

        Output ONLY valid JSON — no markdown, no explanation:
        {"subtasks": [{"description": "..."}, {"description": "..."}]}

        If the goal is simple enough, return a single subtask.
        """;

    public LlmDecomposer(ModelProvider provider, ModelConfig config,
                          WorkerSpec defaultWorkerSpec) {
        this.provider = provider;
        this.config = config;
        this.defaultWorkerSpec = defaultWorkerSpec;
    }

    @Override
    public List<SubTask> decompose(String goal) {
        try {
            var msg = Message.user(PROMPT + "\n\nGoal: " + goal);
            var response = provider.complete(List.of(msg), List.of(), config).join();
            String json = extractJson(response);
            var root = mapper.readTree(json);
            var arr = root.get("subtasks");
            if (arr == null || !arr.isArray() || arr.size() == 0) {
                return fallback(goal);
            }
            var list = new java.util.ArrayList<SubTask>();
            int max = Math.min(arr.size(), 5);
            for (int i = 0; i < max; i++) {
                String desc = arr.get(i).get("description").asText();
                list.add(SubTask.of(desc, defaultWorkerSpec));
            }
            return list;
        } catch (Exception e) {
            return fallback(goal);
        }
    }

    private List<SubTask> fallback(String goal) {
        return List.of(SubTask.of(goal, defaultWorkerSpec));
    }

    private String extractJson(Message msg) {
        String text = msg.contents().stream()
            .filter(c -> c instanceof io.github.frank.harness.ai.protocol.Content.TextContent)
            .map(c -> ((io.github.frank.harness.ai.protocol.Content.TextContent) c).text())
            .collect(java.util.stream.Collectors.joining());
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{\"subtasks\":[]}";
    }
}
```

- [ ] **步骤 4：验证**

```bash
mvn compile -pl harness-orchestrator -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/decompose/
git commit -m "feat: DecompositionStrategy + SubTask + LlmDecomposer"
```

---

### 任务 5：Orchestrator + OrchestratorConfig

**文件：**
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/OrchestratorConfig.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/Orchestrator.java`

- [ ] **步骤 1：创建 OrchestratorConfig.java**

```java
package io.github.frank.harness.orchestrator.orchestrator;

import java.time.Duration;

public record OrchestratorConfig(
    int maxConcurrency,
    Duration perWorkerTimeout,
    int maxWorkerTurns,
    int maxRetries
) {
    public static final OrchestratorConfig DEFAULT = new OrchestratorConfig(
        3, Duration.ofSeconds(300), 10, 1);

    public OrchestratorConfig {
        if (maxConcurrency < 1) maxConcurrency = 1;
        if (maxRetries < 0) maxRetries = 0;
    }
}
```

- [ ] **步骤 2：创建 Orchestrator.java**

```java
package io.github.frank.harness.orchestrator.orchestrator;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.orchestrator.common.OrchestratorResult;
import io.github.frank.harness.orchestrator.common.WorkerResult;
import io.github.frank.harness.orchestrator.orchestrator.decompose.DecompositionStrategy;
import io.github.frank.harness.orchestrator.orchestrator.decompose.SubTask;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.SynthesisStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Orchestrator {
    private final OrchestratorConfig config;
    private final DecompositionStrategy decomposer;
    private final SynthesisStrategy synthesizer;
    private final ModelProvider provider;

    public Orchestrator(OrchestratorConfig config,
                         DecompositionStrategy decomposer,
                         SynthesisStrategy synthesizer,
                         ModelProvider provider) {
        this.config = config;
        this.decomposer = decomposer;
        this.synthesizer = synthesizer;
        this.provider = provider;
    }

    public OrchestratorResult execute(String goal) {
        var tasks = decomposer.decompose(goal);
        if (tasks.isEmpty()) {
            return OrchestratorResult.of("No subtasks generated.", List.of());
        }

        var results = dispatch(tasks);
        String summary = synthesizer.synthesize(goal, results);
        return OrchestratorResult.of(summary, results);
    }

    private List<WorkerResult> dispatch(List<SubTask> tasks) {
        int n = Math.min(config.maxConcurrency(), tasks.size());
        var executor = Executors.newFixedThreadPool(n);
        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();

        for (var task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() ->
                executeWorker(task), executor));
        }

        List<WorkerResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(
                    config.perWorkerTimeout().toMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                results.add(WorkerResult.timeout(tasks.get(i).id()));
            } catch (Exception e) {
                results.add(WorkerResult.error(tasks.get(i).id(), e.getMessage()));
            }
        }
        executor.shutdownNow();
        return results;
    }

    private WorkerResult executeWorker(SubTask task) {
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                var spec = task.spec();
                var agent = Agent.builder()
                    .tools(spec.tools())
                    .config(spec.modelOverride() != null
                        ? spec.modelOverride() : new ModelConfig("gpt-4o"))
                    .sandbox(spec.sandbox())
                    .build();

                if (spec.systemPrompt() != null && !spec.systemPrompt().isBlank()) {
                    agent.setSystemPrompt(spec.systemPrompt());
                }
                agent.prompt(task.description());

                var loop = new AgentLoop(provider);
                var outputRef = new String[]{""};

                // AgentLoop.run() blocks until complete — capture output in callback
                loop.run(agent, event -> {
                    if (event instanceof AgentLifecycleEvent.TurnComplete tc) {
                        outputRef[0] = tc.assistantMessage().contents().stream()
                            .filter(c -> c instanceof Content.TextContent)
                            .map(c -> ((Content.TextContent) c).text())
                            .collect(Collectors.joining());
                    }
                    if (event instanceof AgentLifecycleEvent.ErrorEvent ee) {
                        outputRef[0] = "Error: " + ee.message();
                    }
                });

                return WorkerResult.success(task.id(), outputRef[0]);

            } catch (Exception e) {
                if (attempt == config.maxRetries()) {
                    return WorkerResult.error(task.id(), e.getMessage());
                }
            }
        }
        return WorkerResult.error(task.id(), "Max retries exceeded");
    }
}
```

- [ ] **步骤 3：验证**

```bash
mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/OrchestratorConfig.java \
        harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/orchestrator/Orchestrator.java
git commit -m "feat: Orchestrator — decompose → parallel dispatch → synthesize"
```

---

### 任务 6：HarnessCLI 集成 /orchestrate 命令

**文件：**
- 修改：`harness-coding/src/main/java/io/github/frank/harness/coding/cli/HarnessCLI.java`

- [ ] **步骤 1：在 import 段添加**

```java
import io.github.frank.harness.orchestrator.common.WorkerSpec;
import io.github.frank.harness.orchestrator.orchestrator.Orchestrator;
import io.github.frank.harness.orchestrator.orchestrator.OrchestratorConfig;
import io.github.frank.harness.orchestrator.orchestrator.decompose.LlmDecomposer;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.LlmSynthesizer;
```

- [ ] **步骤 2：在 main() 中 sandbox 变量声明后，添加 orchestrator 构建**

在 `Sandbox sandbox = ...` 之后、`// ── Approval setup` 之前插入：

```java
        // ── Orchestrator setup ────────────────────────────
        var workerSpec = WorkerSpec.of("coder",
            "You are a coding agent. Complete the assigned task precisely. " +
            "Work in: " + workdir,
            List.of(
                BashTool.create(sandbox, Duration.ofMinutes(2)),
                ReadTool.create(workdir),
                WriteTool.create(workdir),
                GrepTool.create(workdir),
                FindTool.create(workdir),
                LsTool.create(workdir),
                PatchTool.create(workdir)
            ));

        var orchestrator = new Orchestrator(
            OrchestratorConfig.DEFAULT,
            new LlmDecomposer(modelProvider, ModelConfig.of("gpt-4o"), workerSpec),
            new LlmSynthesizer(modelProvider, ModelConfig.of("gpt-4o")),
            modelProvider
        );
```

注意：需要将 `ModelProvider modelProvider` 变量提前声明（目前它在 `// ── Tools assembly` 处才声明）。将声明提前。

- [ ] **步骤 3：在 while 循环中 /steer 命令之后，添加 /orchestrate 命令**

```java
            if (input.startsWith("/orchestrate ")) {
                String goal = input.substring(13);
                System.out.println("\n🎯 Orchestrating: " + goal);
                var result = orchestrator.execute(goal);
                System.out.println(result.summary());
                for (var wr : result.workerResults()) {
                    String status = wr.success() ? "✓" : "✗";
                    String preview = wr.output().length() > 120
                        ? wr.output().substring(0, 120) + "..."
                        : wr.output();
                    System.out.printf("  [%s] %s%n", status, preview.replace("\n", " "));
                }
                continue;
            }
```

- [ ] **步骤 4：验证**

```bash
mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-coding/src/main/java/io/github/frank/harness/coding/cli/HarnessCLI.java
git commit -m "feat: add /orchestrate command to CLI — decompose → parallel workers → synthesize"
```

---

### 任务 7：测试 — ConcatSynthesizer + LlmDecomposer

**文件：**
- 创建：`harness-orchestrator/src/test/java/io/github/frank/harness/orchestrator/ConcatSynthesizerTest.java`
- 创建：`harness-orchestrator/src/test/java/io/github/frank/harness/orchestrator/LlmDecomposerTest.java`

- [ ] **步骤 1：创建 ConcatSynthesizerTest.java**

```java
package io.github.frank.harness.orchestrator;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.ConcatSynthesizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConcatSynthesizerTest {
    private final ConcatSynthesizer synth = new ConcatSynthesizer();

    @Test void multipleSuccesses() {
        var results = List.of(
            WorkerResult.success("t1", "Done A"),
            WorkerResult.success("t2", "Done B"));
        String out = synth.synthesize("Test goal", results);
        assertThat(out).contains("Done A", "Done B", "2/2");
    }

    @Test void mixedResults() {
        var results = List.of(
            WorkerResult.success("t1", "OK"),
            WorkerResult.error("t2", "Boom"));
        String out = synth.synthesize("Goal", results);
        assertThat(out).contains("OK", "FAILED", "1/2");
    }

    @Test void allFailed() {
        var results = List.of(
            WorkerResult.timeout("t1"),
            WorkerResult.error("t2", "err"));
        String out = synth.synthesize("Goal", results);
        assertThat(out).contains("0/2");
    }

    @Test void emptyResults() {
        String out = synth.synthesize("Goal", List.of());
        assertThat(out).contains("Goal");
    }
}
```

- [ ] **步骤 2：创建 LlmDecomposerTest.java**

```java
package io.github.frank.harness.orchestrator;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.orchestrator.common.WorkerSpec;
import io.github.frank.harness.orchestrator.orchestrator.decompose.LlmDecomposer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDecomposerTest {
    private LlmDecomposer decomposer;
    private String lastPrompt;

    @BeforeEach void setUp() {
        var workerSpec = WorkerSpec.of("coder", "You are a coder.", List.of());
        ModelProvider mockProvider = new ModelProvider() {
            @Override public String name() { return "mock"; }
            @Override
            public java.util.concurrent.Flow.Publisher<io.github.frank.harness.ai.stream.AssistantEvent>
                    stream(List<Message> ctx, List<io.github.frank.harness.ai.protocol.Tool> tools, ModelConfig cfg) {
                return null;
            }
            @Override
            public CompletableFuture<Message> complete(List<Message> ctx,
                    List<io.github.frank.harness.ai.protocol.Tool> tools, ModelConfig cfg) {
                lastPrompt = ctx.get(0).contents().stream()
                    .filter(c -> c instanceof Content.TextContent)
                    .map(c -> ((Content.TextContent) c).text())
                    .reduce("", (a, b) -> a + b);
                return CompletableFuture.completedFuture(
                    new Message(Role.ASSISTANT, List.of(new Content.TextContent(
                        "{\"subtasks\":[{\"description\":\"Task A\"},{\"description\":\"Task B\"}]}")),
                        "end_turn", java.util.Map.of()));
            }
        };
        decomposer = new LlmDecomposer(mockProvider, new ModelConfig("mock"), workerSpec);
    }

    @Test void normalDecomposition() {
        var tasks = decomposer.decompose("Add tests");
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).description()).isEqualTo("Task A");
    }

    @Test void fallbackOnBadJson() {
        // Create a new decomposer that returns bad JSON
        var workerSpec = WorkerSpec.of("coder", "prompt", List.of());
        ModelProvider badProvider = new ModelProvider() {
            @Override public String name() { return "bad"; }
            @Override public java.util.concurrent.Flow.Publisher<io.github.frank.harness.ai.stream.AssistantEvent>
                    stream(List<Message> ctx, List<io.github.frank.harness.ai.protocol.Tool> tools, ModelConfig cfg) { return null; }
            @Override
            public CompletableFuture<Message> complete(List<Message> ctx,
                    List<io.github.frank.harness.ai.protocol.Tool> tools, ModelConfig cfg) {
                return CompletableFuture.completedFuture(
                    new Message(Role.ASSISTANT, List.of(new Content.TextContent("not json")),
                        "end_turn", java.util.Map.of()));
            }
        };
        var fallbackDecomposer = new LlmDecomposer(badProvider, new ModelConfig("mock"), workerSpec);
        var tasks = fallbackDecomposer.decompose("Original goal");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).description()).isEqualTo("Original goal");
    }
}
```

- [ ] **步骤 3：运行测试**

```bash
mvn test -pl harness-orchestrator -Dtest="ConcatSynthesizerTest,LlmDecomposerTest" -q
```

预期：Tests run: 6，BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-orchestrator/src/test/
git commit -m "test: ConcatSynthesizer + LlmDecomposer — 6 tests including fallback on bad JSON"
```

---

### 任务 8：测试 — Orchestrator 集成

**文件：**
- 创建：`harness-orchestrator/src/test/java/io/github/frank/harness/orchestrator/OrchestratorTest.java`

- [ ] **步骤 1：创建 OrchestratorTest.java**

```java
package io.github.frank.harness.orchestrator;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import io.github.frank.harness.orchestrator.orchestrator.Orchestrator;
import io.github.frank.harness.orchestrator.orchestrator.OrchestratorConfig;
import io.github.frank.harness.orchestrator.orchestrator.decompose.DecompositionStrategy;
import io.github.frank.harness.orchestrator.orchestrator.decompose.SubTask;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.SynthesisStrategy;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorTest {

    private static ModelProvider mockProvider() {
        return new ModelProvider() {
            @Override public String name() { return "mock"; }
            @Override public java.util.concurrent.Flow.Publisher<io.github.frank.harness.ai.stream.AssistantEvent>
                    stream(List<Message> ctx, List<Tool> tools, ModelConfig cfg) { return null; }
            @Override
            public CompletableFuture<Message> complete(List<Message> ctx,
                    List<Tool> tools, ModelConfig cfg) {
                return CompletableFuture.completedFuture(
                    new Message(Role.ASSISTANT, List.of(new Content.TextContent("OK")),
                        "end_turn", java.util.Map.of()));
            }
        };
    }

    @Test void normalOrchestration() {
        DecompositionStrategy decomposer = goal -> List.of(
            SubTask.of("Sub A", null),
            SubTask.of("Sub B", null));
        SynthesisStrategy synth = (goal, results) -> "Done: " + results.size();

        var orchestrator = new Orchestrator(
            OrchestratorConfig.DEFAULT, decomposer, synth, mockProvider());

        var result = orchestrator.execute("Test goal");
        assertThat(result.workerResults()).hasSize(2);
        assertThat(result.summary()).contains("Done");
    }

    @Test void emptyDecomposition() {
        DecompositionStrategy decomposer = goal -> List.of();
        SynthesisStrategy synth = (goal, results) -> "empty";

        var orchestrator = new Orchestrator(
            OrchestratorConfig.DEFAULT, decomposer, synth, mockProvider());

        var result = orchestrator.execute("Test");
        assertThat(result.workerResults()).isEmpty();
    }
}
```

- [ ] **步骤 2：运行测试**

```bash
mvn test -pl harness-orchestrator -Dtest=OrchestratorTest -q
```

预期：Tests run: 2，BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-orchestrator/src/test/java/io/github/frank/harness/orchestrator/OrchestratorTest.java
git commit -m "test: Orchestrator integration — normal flow + empty decomposition"
```

---

### 任务 9：预留包 package-info

**文件：**
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/router/package-info.java`
- 创建：`harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/collaborative/package-info.java`

- [ ] **步骤 1：创建 router/package-info.java**

```java
/**
 * Phase 2 — Intent-based agent routing.
 *
 * <p>Planned: IntentClassifier → AgentRouter → dispatches user requests
 * to pre-configured specialized agents (coder, reviewer, researcher).
 * Reserved package — no implementation yet.
 */
package io.github.frank.harness.orchestrator.router;
```

- [ ] **步骤 2：创建 collaborative/package-info.java**

```java
/**
 * Phase 3 — Collaborative multi-agent dialogue.
 *
 * <p>Planned: Blackboard (shared context), TurnCoordinator (agent turn ordering),
 * AgentMessageBus (direct agent-to-agent messaging).
 * Reserved package — no implementation yet.
 */
package io.github.frank.harness.orchestrator.collaborative;
```

- [ ] **步骤 3：验证**

```bash
mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/router/ \
        harness-orchestrator/src/main/java/io/github/frank/harness/orchestrator/collaborative/
git commit -m "feat: reserve router/ and collaborative/ packages for Phase 2-3"
```

---

### 任务 10：全量编译 + 全量测试验证

- [ ] **步骤 1：全量编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 2：运行全部测试**

```bash
mvn test -q 2>&1 | grep -E "Tests run:|BUILD"
```

预期：Total tests: 25 (17 existing + 8 new)，FAILURES: 0，BUILD SUCCESS

- [ ] **步骤 3：验证 git status**

```bash
git status
```

预期：working tree clean

- [ ] **步骤 4：Commit**

```bash
git add -A && git commit -m "chore: final verification — all 25 tests pass, clean tree"
```

---

## 审查点

| 任务 | 审查内容 |
|------|---------|
| 2 | WorkerSpec.sandbox 为 null 时的默认行为 |
| 3 | LlmSynthesizer 的 extractText 是否正确处理空内容 |
| 4 | LlmDecomposer JSON 解析的容错边界 |
| 5 | Orchestrator 线程安全（CountDownLatch + AgentLoop） |
| 6 | HarnessCLI 中 modelProvider 变量声明位置是否提前 |
| 7-8 | Mock ModelProvider 正确实现了接口 |
