package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.orchestrator.common.WorkerResult;
import java.util.List;
import java.util.stream.Collectors;

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
        return response.contents().stream()
            .filter(c -> c instanceof Content.TextContent)
            .map(c -> ((Content.TextContent) c).text())
            .collect(Collectors.joining());
    }
}
