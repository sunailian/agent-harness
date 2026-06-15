package io.github.frank.harness.orchestrator;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.stream.AssistantEvent;
import io.github.frank.harness.orchestrator.common.WorkerSpec;
import io.github.frank.harness.orchestrator.orchestrator.decompose.LlmDecomposer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDecomposerTest {

    private static ModelProvider mockProvider(String responseJson) {
        return new ModelProvider() {
            @Override public String name() { return "mock"; }
            @Override
            public void stream(List<Message> ctx, List<Tool> tools, ModelConfig cfg,
                    Consumer<AssistantEvent> onEvent) {
                // not used by decomposer
            }
            @Override
            public CompletableFuture<Message> complete(List<Message> ctx,
                    List<Tool> tools, ModelConfig cfg) {
                return CompletableFuture.completedFuture(
                    new Message(Role.ASSISTANT, List.of(new Content.TextContent(responseJson)),
                        "end_turn", Map.of()));
            }
        };
    }

    @Test void normalDecomposition() {
        var provider = mockProvider(
            "{\"subtasks\":[{\"description\":\"Task A\"},{\"description\":\"Task B\"}]}");
        var spec = WorkerSpec.of("coder", "You are a coder.", List.of());
        var decomposer = new LlmDecomposer(provider, ModelConfig.of("mock"), spec);
        var tasks = decomposer.decompose("Add tests");
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).description()).isEqualTo("Task A");
    }

    @Test void fallbackOnBadJson() {
        var provider = mockProvider("not json at all");
        var spec = WorkerSpec.of("coder", "prompt", List.of());
        var decomposer = new LlmDecomposer(provider, ModelConfig.of("mock"), spec);
        var tasks = decomposer.decompose("Original goal");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).description()).isEqualTo("Original goal");
    }

    @Test void fallbackOnEmptySubtasks() {
        var provider = mockProvider("{\"subtasks\":[]}");
        var spec = WorkerSpec.of("coder", "prompt", List.of());
        var decomposer = new LlmDecomposer(provider, ModelConfig.of("mock"), spec);
        var tasks = decomposer.decompose("Some goal");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).description()).isEqualTo("Some goal");
    }
}
