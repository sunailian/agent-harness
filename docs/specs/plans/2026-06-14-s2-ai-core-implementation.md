# S2: harness-ai-core 协议抽象 + OpenAI Provider 实现计划

> **目标**: 实现 Message/Tool/AssistantEvent 协议 + streamSimple() + OpenAI Provider（纯 OkHttp + SSE 解析）

**架构**: protocol/ → provider/ → stream/ 三层，用 sealed interface 定义内容块，WireMock 替代品用 OkHttp MockWebServer 做 HTTP 测试

---

## 文件结构

```
harness-ai-core/src/main/java/io/github/frank/harness/ai/
├── protocol/
│   ├── Role.java
│   ├── Content.java              # sealed interface + 4 records
│   ├── Message.java
│   ├── Tool.java
│   ├── JsonSchema.java
│   └── ToolResult.java
├── provider/
│   ├── ModelProvider.java        # 接口
│   ├── ModelConfig.java
│   └── openai/
│       └── OpenAIProvider.java   # OkHttp + SSE 实现
├── stream/
│   └── AssistantEvent.java       # sealed interface + 6 records
└── package-info.java             # (已存在，更新)
```

---

### 任务 1：Content 密封接口 + 4 个 record

**文件：**
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/Role.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/Content.java`

- [ ] **步骤 1：创建 Role 枚举**

```java
package io.github.frank.harness.ai.protocol;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
```

- [ ] **步骤 2：创建 Content 密封接口 + 4 个 record**

```java
package io.github.frank.harness.ai.protocol;

public sealed interface Content
        permits Content.TextContent, Content.ThinkContent,
                Content.ToolCallContent, Content.ToolResultContent {

    record TextContent(String text) implements Content {}
    record ThinkContent(String reasoning) implements Content {}
    record ToolCallContent(String id, String name, String arguments) implements Content {}
    record ToolResultContent(String callId, String name, String output, boolean isError) implements Content {}
}
```

- [ ] **步骤 3：验证编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -pl harness-ai-core
```
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/
git commit -m "feat(ai-core): add Content sealed interface + Role enum"
```

---

### 任务 2：Message + Tool + JsonSchema + ToolResult

**文件：**
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/Message.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/ToolResult.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/JsonSchema.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/Tool.java`

- [ ] **步骤 1：创建 Message record**

```java
package io.github.frank.harness.ai.protocol;

import java.util.List;
import java.util.Map;

public record Message(
    Role role,
    List<Content> contents,
    String stopReason,
    Map<String, Object> metadata
) {
    public Message {
        if (contents == null) contents = List.of();
        if (metadata == null) metadata = Map.of();
    }

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new Content.TextContent(text)), null, Map.of());
    }

    public static Message system(String text) {
        return new Message(Role.SYSTEM, List.of(new Content.TextContent(text)), null, Map.of());
    }

    public static Message assistant(List<Content> contents, String stopReason) {
        return new Message(Role.ASSISTANT, contents, stopReason, Map.of());
    }

    public static Message tool(List<Content.ToolResultContent> results) {
        return new Message(Role.TOOL, List.copyOf(results), null, Map.of());
    }
}
```

- [ ] **步骤 2：创建 ToolResult record**

```java
package io.github.frank.harness.ai.protocol;

public record ToolResult(
    boolean success,
    String output,
    boolean terminate
) {
    public static ToolResult success(String output) {
        return new ToolResult(true, output, false);
    }

    public static ToolResult error(String output) {
        return new ToolResult(false, output, false);
    }

    public static ToolResult blocked(String toolName) {
        return new ToolResult(false, "Tool '" + toolName + "' execution blocked by hook", false);
    }

    public ToolResult withTerminate() {
        return new ToolResult(success, output, true);
    }

    public Content.ToolResultContent toMessage(String callId, String name) {
        return new Content.ToolResultContent(callId, name, output, !success);
    }
}
```

- [ ] **步骤 3：创建 JsonSchema record**

```java
package io.github.frank.harness.ai.protocol;

import java.util.List;
import java.util.Map;

public record JsonSchema(
    String type,
    Map<String, PropertyDef> properties,
    List<String> required
) {
    public record PropertyDef(String type, String description, Object defaultValue) {}

    public JsonSchema {
        if (type == null) type = "object";
        if (properties == null) properties = Map.of();
        if (required == null) required = List.of();
    }
}
```

- [ ] **步骤 4：创建 Tool record**

```java
package io.github.frank.harness.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;

public record Tool(
    String name,
    String description,
    JsonSchema parameters,
    Function<JsonNode, ToolResult> execute
) {}
```

- [ ] **步骤 5：验证编译**

```bash
mvn compile -pl harness-ai-core
```
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add harness-ai-core/src/main/java/io/github/frank/harness/ai/protocol/
git commit -m "feat(ai-core): add Message, Tool, JsonSchema, ToolResult"
```

---

### 任务 3：AssistantEvent 密封接口 + ModelProvider 接口

**文件：**
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/stream/AssistantEvent.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/provider/ModelProvider.java`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/provider/ModelConfig.java`

- [ ] **步骤 1：创建 AssistantEvent 密封接口**

```java
package io.github.frank.harness.ai.stream;

import java.util.Map;

public sealed interface AssistantEvent
        permits AssistantEvent.TextDelta, AssistantEvent.ThinkDelta,
                AssistantEvent.ToolCallStart, AssistantEvent.ToolCallArgs,
                AssistantEvent.ToolCallDone, AssistantEvent.Done {

    record TextDelta(int index, String text) implements AssistantEvent {}
    record ThinkDelta(int index, String reasoning) implements AssistantEvent {}
    record ToolCallStart(String id, String name) implements AssistantEvent {}
    record ToolCallArgs(String id, String delta) implements AssistantEvent {}
    record ToolCallDone(String id, String name, String arguments) implements AssistantEvent {}
    record Done(String stopReason, Map<String, Object> usage) implements AssistantEvent {}
}
```

- [ ] **步骤 2：创建 ModelConfig record**

```java
package io.github.frank.harness.ai.provider;

import java.util.Map;

public record ModelConfig(
    String model,
    Double temperature,
    Integer maxTokens,
    Map<String, Object> extra
) {
    public ModelConfig {
        if (temperature == null) temperature = 0.1;
        if (extra == null) extra = Map.of();
    }

    public static ModelConfig of(String model) {
        return new ModelConfig(model, 0.1, 4096, Map.of());
    }
}
```

- [ ] **步骤 3：创建 ModelProvider 接口**

```java
package io.github.frank.harness.ai.provider;

import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.stream.AssistantEvent;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ModelProvider {
    String name();
    Publisher<AssistantEvent> stream(List<Message> context, List<Tool> tools, ModelConfig config);
    CompletableFuture<Message> complete(List<Message> context, List<Tool> tools, ModelConfig config);
}
```

**注意：** `Publisher<AssistantEvent>` 来自 `org.reactivestreams:reactive-streams:1.0.4`。由于本机 Maven 中央仓库不可达，改为使用自定义回调接口替代 Reactive Streams。实际修改为：

```java
package io.github.frank.harness.ai.provider;

import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.stream.AssistantEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ModelProvider {
    String name();

    /** 流式请求，每收到一个事件回调 consumer */
    void stream(List<Message> context, List<Tool> tools, ModelConfig config, Consumer<AssistantEvent> onEvent);

    /** 非流式请求 */
    CompletableFuture<Message> complete(List<Message> context, List<Tool> tools, ModelConfig config);
}
```

- [ ] **步骤 4：验证编译**

```bash
mvn compile -pl harness-ai-core
```
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-ai-core/src/main/java/io/github/frank/harness/ai/stream/ harness-ai-core/src/main/java/io/github/frank/harness/ai/provider/
git commit -m "feat(ai-core): add AssistantEvent, ModelProvider, ModelConfig"
```

---

### 任务 4：OpenAI Provider 实现（核心）

**文件：**
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/provider/openai/OpenAIProvider.java`

- [ ] **步骤 1：创建 OpenAIProvider**

```java
package io.github.frank.harness.ai.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.stream.AssistantEvent;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OpenAIProvider implements ModelProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final OkHttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public OpenAIProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public OpenAIProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = new OkHttpClient();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void stream(List<Message> context, List<Tool> tools, ModelConfig config,
                       Consumer<AssistantEvent> onEvent) {
        try {
            var body = buildRequestBody(context, tools, config, true);
            var request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body, JSON))
                    .build();

            var factory = EventSources.createFactory(http);
            var listener = new SseListener(onEvent);
            factory.newEventSource(request, listener);

        } catch (JsonProcessingException e) {
            log.error("Failed to build request body", e);
            onEvent.accept(new AssistantEvent.Done("error", Map.of("error", e.getMessage())));
        }
    }

    @Override
    public CompletableFuture<Message> complete(List<Message> context, List<Tool> tools, ModelConfig config) {
        var future = new CompletableFuture<Message>();
        try {
            var body = buildRequestBody(context, tools, config, false);
            var request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body, JSON))
                    .build();

            http.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    try (response) {
                        var json = mapper.readTree(response.body().string());
                        var msg = parseResponseMessage(json);
                        future.complete(msg);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (JsonProcessingException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // --- Private helpers ---

    private String buildRequestBody(List<Message> context, List<Tool> tools,
                                    ModelConfig config, boolean stream) throws JsonProcessingException {
        var root = mapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", stream);
        if (config.temperature() != null) root.put("temperature", config.temperature());
        if (config.maxTokens() != null) root.put("max_tokens", config.maxTokens());

        // Messages
        var messages = root.putArray("messages");
        for (var msg : context) {
            var m = messages.addObject();
            m.put("role", toOpenAIRole(msg.role()));
            // Build content — handle multi-block messages
            var textBuilder = new StringBuilder();
            var toolCalls = mapper.createArrayNode();
            for (var block : msg.contents()) {
                switch (block) {
                    case Content.TextContent t -> textBuilder.append(t.text());
                    case Content.ToolCallContent tc -> {
                        var tcNode = toolCalls.addObject();
                        tcNode.put("id", tc.id());
                        tcNode.put("type", "function");
                        var func = tcNode.putObject("function");
                        func.put("name", tc.name());
                        func.put("arguments", tc.arguments());
                    }
                    case Content.ToolResultContent tr -> {
                        m.put("tool_call_id", tr.callId());
                        m.put("role", "tool");
                        textBuilder.append(tr.output());
                    }
                    case Content.ThinkContent ignored -> {} // OpenAI doesn't natively support thinking
                }
            }
            if (toolCalls.size() > 0) {
                m.set("tool_calls", toolCalls);
            }
            if (!textBuilder.isEmpty()) {
                m.put("content", textBuilder.toString());
            }
        }

        // Tools
        if (!tools.isEmpty()) {
            var toolArr = root.putArray("tools");
            for (var tool : tools) {
                var t = toolArr.addObject();
                t.put("type", "function");
                var func = t.putObject("function");
                func.put("name", tool.name());
                func.put("description", tool.description());
                func.set("parameters", mapper.valueToTree(tool.parameters()));
            }
        }

        return mapper.writeValueAsString(root);
    }

    private String toOpenAIRole(Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    private Message parseResponseMessage(JsonNode root) {
        var choice = root.path("choices").get(0);
        var msg = choice.path("message");
        var contents = new ArrayList<Content>();
        var stopReason = choice.path("finish_reason").asText("stop");

        // Text content
        if (msg.has("content") && !msg.path("content").isNull()) {
            contents.add(new Content.TextContent(msg.path("content").asText()));
        }

        // Tool calls
        if (msg.has("tool_calls")) {
            for (var tc : msg.path("tool_calls")) {
                var func = tc.path("function");
                contents.add(new Content.ToolCallContent(
                        tc.path("id").asText(),
                        func.path("name").asText(),
                        func.path("arguments").asText()
                ));
            }
        }

        var usage = Map.<String, Object>of(
                "prompt_tokens", root.path("usage").path("prompt_tokens").asInt(),
                "completion_tokens", root.path("usage").path("completion_tokens").asInt()
        );

        return new Message(Role.ASSISTANT, contents,
                "tool_calls".equals(stopReason) ? "tool_use" : stopReason,
                usage);
    }

    /** SSE event parser — converts OpenAI streaming chunks to AssistantEvent */
    private static class SseListener extends EventSourceListener {
        private final Consumer<AssistantEvent> onEvent;
        private final Map<String, String> toolCallNames = new HashMap<>();
        private final Map<String, StringBuilder> toolCallArgs = new HashMap<>();
        private int textIndex = 0;

        SseListener(Consumer<AssistantEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            if ("[DONE]".equals(data.trim())) {
                toolCallNames.clear();
                toolCallArgs.clear();
                return;
            }
            try {
                var delta = mapper.readTree(data)
                        .path("choices").get(0)
                        .path("delta");

                // Text delta
                if (delta.has("content")) {
                    onEvent.accept(new AssistantEvent.TextDelta(textIndex++, delta.path("content").asText()));
                }

                // Tool call delta
                if (delta.has("tool_calls")) {
                    for (var tc : delta.path("tool_calls")) {
                        var callId = tc.path("index").asText("0");
                        var func = tc.path("function");

                        if (func.has("name")) {
                            String name = func.path("name").asText();
                            toolCallNames.put(callId, name);
                            onEvent.accept(new AssistantEvent.ToolCallStart(callId, name));
                        }
                        if (func.has("arguments")) {
                            toolCallArgs.computeIfAbsent(callId, k -> new StringBuilder())
                                    .append(func.path("arguments").asText());
                            onEvent.accept(new AssistantEvent.ToolCallArgs(callId,
                                    func.path("arguments").asText()));
                        }
                    }
                }

                // Finish reason
                var finishReason = delta.path("").path("finish_reason");
                if (!finishReason.isMissingNode()) {
                    // Notify tool call completion
                    for (var entry : toolCallNames.entrySet()) {
                        var callId = entry.getKey();
                        onEvent.accept(new AssistantEvent.ToolCallDone(
                                callId, entry.getValue(),
                                toolCallArgs.getOrDefault(callId, new StringBuilder()).toString()));
                    }
                }

            } catch (Exception e) {
                log.warn("Failed to parse SSE event: {}", data, e);
            }
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            log.error("SSE connection failed", t);
            onEvent.accept(new AssistantEvent.Done("error",
                    Map.of("error", t.getMessage() != null ? t.getMessage() : "SSE connection failed")));
        }
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
mvn compile -pl harness-ai-core
```
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-ai-core/src/main/java/io/github/frank/harness/ai/provider/openai/
git commit -m "feat(ai-core): add OpenAIProvider with SSE streaming support"
```

---

### 任务 5：更新 package-info.java + 验证全量编译

- [ ] **步骤 1：更新 package-info.java 添加新模块说明**

```java
/**
 * Harness AI Core — 模型适配层.
 *
 * <h2>职责</h2>
 * 把不同模型供应商的差异统一成 Message、Tool、AssistantEvent 和 stream()。
 * 上层 Agent Loop 不需要知道底层是 OpenAI 还是 Anthropic。
 *
 * <h2>包结构</h2>
 * <ul>
 *   <li>{@code protocol} — Message / Tool / Content / JsonSchema 协议类型</li>
 *   <li>{@code provider} — ModelProvider 接口 + ModelConfig</li>
 *   <li>{@code provider.openai} — OpenAIProvider（OkHttp + SSE）</li>
 *   <li>{@code stream} — AssistantEvent 流式事件体系</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * var provider = new OpenAIProvider("sk-...");
 * provider.stream(messages, tools, config, event -> {
 *     switch (event) {
 *         case TextDelta t -> System.out.print(t.text());
 *         case ToolCallStart s -> System.out.println("Calling: " + s.name());
 *         case Done d -> System.out.println("Done: " + d.stopReason());
 *         default -> {}
 *     }
 * });
 * }</pre>
 */
package io.github.frank.harness.ai;
```

- [ ] **步骤 2：全量编译验证**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile
```
预期：BUILD SUCCESS — 5 模块（+父 POM + BOM）全部通过

- [ ] **步骤 3：验证最终文件结构**

```bash
find harness-ai-core/src -name "*.java" | sort
```

- [ ] **步骤 4：Commit**

```bash
git add -A
git commit -m "feat(ai-core): finalize protocol layer — 10 types, OpenAI provider, SSE parsing"
```
