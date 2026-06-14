package io.github.frank.harness.ai.provider;

import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.stream.AssistantEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Model provider abstraction.
 * <p>
 * Using {@link Consumer} callback instead of Reactive Streams Publisher
 * to avoid external dependency (reactive-streams not available in this env).
 */
public interface ModelProvider {

    String name();

    /** Streaming request — calls onEvent for each received event. */
    void stream(List<Message> context, List<Tool> tools, ModelConfig config, Consumer<AssistantEvent> onEvent);

    /** Non-streaming request. */
    CompletableFuture<Message> complete(List<Message> context, List<Tool> tools, ModelConfig config);
}
