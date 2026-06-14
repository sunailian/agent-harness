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

/**
 * OpenAI Chat Completions provider using OkHttp + SSE streaming.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Streaming responses via SSE (text deltas, tool call deltas)</li>
 *   <li>Non-streaming complete() for simple requests</li>
 *   <li>Tool/function calling via the native OpenAI tools API</li>
 *   <li>Custom base URL for OpenAI-compatible endpoints</li>
 * </ul>
 */
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
            var listener = new OpenAISseListener(onEvent);
            factory.newEventSource(request, listener);

        } catch (JsonProcessingException e) {
            log.error("Failed to build request body", e);
            onEvent.accept(new AssistantEvent.Done("error",
                    Map.of("error", e.getMessage())));
        }
    }

    @Override
    public CompletableFuture<Message> complete(List<Message> context, List<Tool> tools,
                                                ModelConfig config) {
        var future = new CompletableFuture<Message>();
        try {
            var body = buildRequestBody(context, tools, config, false);
            var request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body, JSON))
                    .build();

            http.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
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

    // ── Request building ──────────────────────────────────────────

    private String buildRequestBody(List<Message> context, List<Tool> tools,
                                    ModelConfig config, boolean stream)
            throws JsonProcessingException {
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
                    case Content.ThinkContent ignored -> {
                        // OpenAI doesn't natively expose thinking blocks
                    }
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

    private static String toOpenAIRole(Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    // ── Response parsing ──────────────────────────────────────────

    private Message parseResponseMessage(JsonNode root) {
        var choice = root.path("choices").get(0);
        var msg = choice.path("message");
        var contents = new ArrayList<Content>();
        var stopReason = choice.path("finish_reason").asText("stop");

        // Text
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
                        func.path("arguments").asText()));
            }
        }

        var usage = Map.<String, Object>of(
                "prompt_tokens", root.path("usage").path("prompt_tokens").asInt(),
                "completion_tokens", root.path("usage").path("completion_tokens").asInt());

        return new Message(Role.ASSISTANT, contents,
                "tool_calls".equals(stopReason) ? "tool_use" : stopReason,
                usage);
    }

    // ── SSE event parser ──────────────────────────────────────────

    /**
     * Converts OpenAI streaming SSE chunks into AssistantEvent objects.
     * Tracks tool call names and aggregates arguments across deltas.
     */
    private static class OpenAISseListener extends EventSourceListener {
        private final Consumer<AssistantEvent> onEvent;
        private final Map<String, String> toolCallNames = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolCallArgs = new HashMap<>();
        private int textIndex = 0;

        OpenAISseListener(Consumer<AssistantEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            if ("[DONE]".equals(data.trim())) {
                return;
            }
            try {
                var delta = mapper.readTree(data)
                        .path("choices").get(0)
                        .path("delta");

                // Text delta
                if (delta.has("content")) {
                    onEvent.accept(new AssistantEvent.TextDelta(
                            textIndex++, delta.path("content").asText()));
                }

                // Tool call delta
                if (delta.has("tool_calls")) {
                    for (var tc : delta.path("tool_calls")) {
                        var callId = tc.has("id") ? tc.path("id").asText()
                                : String.valueOf(tc.path("index").asInt(0));
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

                // Finish reason — extract from choice level
                var finishReason = mapper.readTree(data).path("choices").get(0)
                        .path("finish_reason");
                if (!finishReason.isMissingNode() && !finishReason.asText().isEmpty()) {
                    emitPendingToolCallDones();
                }

            } catch (Exception e) {
                log.warn("Failed to parse SSE event: {}", data, e);
            }
        }

        @Override
        public void onClosed(EventSource eventSource) {
            emitPendingToolCallDones();
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            log.error("SSE connection failed", t);
            onEvent.accept(new AssistantEvent.Done("error",
                    Map.of("error", t.getMessage() != null ? t.getMessage()
                            : "SSE connection failed")));
        }

        private void emitPendingToolCallDones() {
            for (var entry : toolCallNames.entrySet()) {
                var callId = entry.getKey();
                onEvent.accept(new AssistantEvent.ToolCallDone(
                        callId, entry.getValue(),
                        toolCallArgs.getOrDefault(callId, new StringBuilder()).toString()));
            }
            toolCallNames.clear();
            toolCallArgs.clear();
        }
    }
}
