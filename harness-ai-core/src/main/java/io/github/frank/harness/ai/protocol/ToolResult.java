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
