package io.github.frank.harness.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.ToolResult;

import java.util.List;

public class HookChain {
    private final List<ToolHook> hooks;

    public HookChain(List<ToolHook> hooks) {
        this.hooks = List.copyOf(hooks);
    }

    public boolean runBefore(Content.ToolCallContent call, JsonNode args) {
        for (var hook : hooks) {
            if (!hook.beforeToolCall(call, args)) return false;
        }
        return true;
    }

    public ToolResult runAfter(Content.ToolCallContent call, ToolResult result) {
        ToolResult r = result;
        for (var hook : hooks) {
            r = hook.afterToolCall(call, r);
        }
        return r;
    }
}
