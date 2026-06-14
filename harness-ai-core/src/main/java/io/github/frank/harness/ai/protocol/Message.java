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
