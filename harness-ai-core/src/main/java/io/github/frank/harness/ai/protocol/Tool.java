package io.github.frank.harness.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;

public record Tool(
    String name,
    String description,
    JsonSchema parameters,
    Function<JsonNode, ToolResult> execute
) {}
