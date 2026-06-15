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
