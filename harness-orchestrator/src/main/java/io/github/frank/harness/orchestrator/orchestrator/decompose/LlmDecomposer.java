package io.github.frank.harness.orchestrator.orchestrator.decompose;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.orchestrator.common.WorkerSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LlmDecomposer implements DecompositionStrategy {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ModelProvider provider;
    private final ModelConfig config;
    private final WorkerSpec defaultWorkerSpec;

    private static final String PROMPT = """
        Break the goal into 2-5 independent subtasks that can run in parallel.
        Each subtask must be a complete, actionable instruction.

        Output ONLY valid JSON — no markdown, no explanation:
        {"subtasks": [{"description": "..."}, {"description": "..."}]}

        If the goal is simple enough, return a single subtask.
        """;

    public LlmDecomposer(ModelProvider provider, ModelConfig config, WorkerSpec defaultWorkerSpec) {
        this.provider = provider;
        this.config = config;
        this.defaultWorkerSpec = defaultWorkerSpec;
    }

    @Override
    public List<SubTask> decompose(String goal) {
        try {
            var msg = Message.user(PROMPT + "\n\nGoal: " + goal);
            var response = provider.complete(List.of(msg), List.of(), config).join();
            String json = extractJson(response);
            var root = mapper.readTree(json);
            var arr = root.get("subtasks");
            if (arr == null || !arr.isArray() || arr.size() == 0) {
                return fallback(goal);
            }
            var list = new ArrayList<SubTask>();
            int max = Math.min(arr.size(), 5);
            for (int i = 0; i < max; i++) {
                String desc = arr.get(i).get("description").asText();
                list.add(SubTask.of(desc, defaultWorkerSpec));
            }
            return list;
        } catch (Exception e) {
            return fallback(goal);
        }
    }

    private List<SubTask> fallback(String goal) {
        return List.of(SubTask.of(goal, defaultWorkerSpec));
    }

    private String extractJson(Message msg) {
        String text = msg.contents().stream()
            .filter(c -> c instanceof Content.TextContent)
            .map(c -> ((Content.TextContent) c).text())
            .collect(Collectors.joining());
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{\"subtasks\":[]}";
    }
}
