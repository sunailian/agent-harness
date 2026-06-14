package io.github.frank.harness.ai.protocol;

public sealed interface Content
        permits Content.TextContent, Content.ThinkContent,
                Content.ToolCallContent, Content.ToolResultContent {

    record TextContent(String text) implements Content {}
    record ThinkContent(String reasoning) implements Content {}
    record ToolCallContent(String id, String name, String arguments) implements Content {}
    record ToolResultContent(String callId, String name, String output, boolean isError) implements Content {}
}
