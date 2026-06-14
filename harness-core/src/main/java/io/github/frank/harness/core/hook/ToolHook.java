package io.github.frank.harness.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.ToolResult;

public interface ToolHook {
    boolean beforeToolCall(Content.ToolCallContent call, JsonNode args);
    ToolResult afterToolCall(Content.ToolCallContent call, ToolResult result);
}
