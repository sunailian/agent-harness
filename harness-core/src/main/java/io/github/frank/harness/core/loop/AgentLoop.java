package io.github.frank.harness.core.loop;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.protocol.ToolResult;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.stream.AssistantEvent;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.agent.AgentState;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * AgentLoop — double-loop execution engine.
 *
 * <h2>Architecture</h2>
 * <pre>
 * outerLoop:
 *   while (!STOPPED) {
 *     innerLoop()          // steering + tool calls
 *     if (followUp) → continue, else → break
 *   }
 *
 * innerLoop:
 *   while (RUNNING) {
 *     drainSteering()      // inject user mid-turn messages
 *     streamAssistant()    // call model
 *     if (no tool calls) → return
 *     executeTools()       // prepare → hook → execute → finalize
 *   }
 * </pre>
 *
 * <h2>Why loop doesn't touch disk</h2>
 * AgentLoop only emits events and messages. The product layer (harness-coding)
 * is responsible for persistence (JSONL), UI rendering, and resource loading.
 * This makes the loop fully testable with mock providers.
 */
public class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final ModelProvider provider;
    private final ExecutorService executor;

    public AgentLoop(ModelProvider provider) {
        this.provider = provider;
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * Run the full AgentLoop, calling onEvent for each lifecycle event.
     * This method blocks until the loop completes or is aborted.
     */
    public void run(Agent agent, Consumer<AgentLifecycleEvent> onEvent) {
        try {
            agent.setState(AgentState.RUNNING);
            onEvent.accept(new AgentLifecycleEvent.LoopStarted());

            outerLoop(agent, onEvent);

        } catch (Exception e) {
            log.error("AgentLoop crashed", e);
            onEvent.accept(new AgentLifecycleEvent.ErrorEvent("AgentLoop crashed", e));
        } finally {
            agent.setState(AgentState.STOPPED);
            onEvent.accept(new AgentLifecycleEvent.LoopComplete(
                    agent.getState() == AgentState.STOPPING ? "aborted" : "end_turn"));
        }
    }

    // ── Outer loop: handles follow-up tasks ───────────────────

    private void outerLoop(Agent agent, Consumer<AgentLifecycleEvent> onEvent) {
        while (agent.getState() == AgentState.RUNNING) {
            innerLoop(agent, onEvent);

            if (agent.getState() != AgentState.RUNNING) break;

            // Check follow-up queue
            var followUp = agent.getFollowUp().poll();
            if (followUp != null) {
                agent.prompt(followUp);
            } else {
                break; // No follow-up — end outer loop
            }
        }
    }

    // ── Inner loop: steering + model + tools ──────────────────

    private void innerLoop(Agent agent, Consumer<AgentLifecycleEvent> onEvent) {
        while (agent.getState() == AgentState.RUNNING) {
            // 1. Drain steering messages
            drainSteering(agent);

            // 2. Stream assistant response
            var assistantBuilder = new AssistantCollector();
            streamAssistant(agent, onEvent, assistantBuilder);

            // 3. Check result
            if (assistantBuilder.errorMessage != null) {
                onEvent.accept(new AgentLifecycleEvent.ErrorEvent(
                        assistantBuilder.errorMessage, null));
                return;
            }

            var assistant = assistantBuilder.build();
            if (assistant == null) return;

            agent.addMessage(assistant);
            onEvent.accept(new AgentLifecycleEvent.TurnComplete(assistant));

            // 4. Extract tool calls
            var toolCalls = assistant.contents().stream()
                    .filter(c -> c instanceof Content.ToolCallContent)
                    .map(c -> (Content.ToolCallContent) c)
                    .toList();

            if (toolCalls.isEmpty()) {
                return; // No tools → back to outer loop for follow-up check
            }

            // 5. Execute tools (concurrent, ordered write-back)
            executeTools(toolCalls, agent, onEvent);
        }
    }

    // ── Steering drain ────────────────────────────────────────

    private void drainSteering(Agent agent) {
        var text = agent.getSteering().drain();
        if (!text.isEmpty()) {
            agent.addMessage(Message.user("[STEERING] " + text));
        }
    }

    // ── Model streaming ───────────────────────────────────────

    private void streamAssistant(Agent agent, Consumer<AgentLifecycleEvent> onEvent,
                                 AssistantCollector collector) {
        var latch = new java.util.concurrent.CountDownLatch(1);

        provider.stream(agent.getContext(), agent.getTools(), agent.getConfig(), event -> {
            switch (event) {
                case AssistantEvent.TextDelta td -> {
                    collector.appendText(td.text());
                    onEvent.accept(new AgentLifecycleEvent.TextDelta(td.text()));
                }
                case AssistantEvent.ToolCallStart tcs -> collector.startToolCall(tcs.id(), tcs.name());
                case AssistantEvent.ToolCallArgs tca -> collector.appendToolArgs(tca.id(), tca.delta());
                case AssistantEvent.ToolCallDone tcd -> collector.finishToolCall(tcd.id(), tcd.name(), tcd.arguments());
                case AssistantEvent.Done d -> {
                    collector.stopReason = d.stopReason();
                    collector.usage = d.usage();
                    latch.countDown();
                }
                default -> {} // ThinkDelta not forwarded yet
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            collector.errorMessage = "Interrupted while waiting for model response";
        }
    }

    // ── Tool execution pipeline ───────────────────────────────

    private void executeTools(List<Content.ToolCallContent> toolCalls,
                              Agent agent, Consumer<AgentLifecycleEvent> onEvent) {
        int n = toolCalls.size();

        @SuppressWarnings("unchecked")
        CompletableFuture<ToolResult>[] futures = new CompletableFuture[n];

        for (int i = 0; i < n; i++) {
            final int index = i;
            var call = toolCalls.get(i);

            futures[i] = CompletableFuture.supplyAsync(() -> {
                // Phase 1: Prepare
                var tool = agent.findTool(call.name());
                if (tool == null) {
                    var result = ToolResult.error("Unknown tool: " + call.name());
                    onEvent.accept(new AgentLifecycleEvent.ToolCallEnd(call, result));
                    return result;
                }
                var args = agent.validateArgs(tool, call.arguments());

                // Phase 2: Before hook
                if (!agent.getHooks().runBefore(call, args)) {
                    var result = ToolResult.blocked(call.name());
                    onEvent.accept(new AgentLifecycleEvent.ToolCallEnd(call, result));
                    return result;
                }

                // Phase 3: Execute
                onEvent.accept(new AgentLifecycleEvent.ToolCallStart(call));
                ToolResult result;
                try {
                    result = tool.execute().apply(args);
                } catch (Exception e) {
                    result = ToolResult.error("Tool execution failed: " + e.getMessage());
                }

                // Phase 4: After hook
                result = agent.getHooks().runAfter(call, result);

                onEvent.accept(new AgentLifecycleEvent.ToolCallEnd(call, result));
                return result;
            }, executor);
        }

        // Wait for all tools to complete
        CompletableFuture.allOf(futures).join();

        // Write results back in original order (not completion order!)
        for (int i = 0; i < n; i++) {
            var call = toolCalls.get(i);
            var result = futures[i].join();
            agent.addMessage(new Message(Role.TOOL,
                    List.of(result.toMessage(call.id(), call.name())),
                    null, Map.of()));
        }
    }

    // ── Collector helper ──────────────────────────────────────

    private static class AssistantCollector {
        final StringBuilder text = new StringBuilder();
        final List<Content.ToolCallContent> toolCalls = new ArrayList<>();
        final java.util.Map<String, String> toolCallNames = new java.util.LinkedHashMap<>();
        final java.util.Map<String, StringBuilder> toolCallArgs = new java.util.HashMap<>();
        String stopReason = "end_turn";
        Map<String, Object> usage = Map.of();
        String errorMessage;

        void appendText(String t) { text.append(t); }
        void startToolCall(String id, String name) { toolCallNames.put(id, name); }
        void appendToolArgs(String id, String delta) {
            toolCallArgs.computeIfAbsent(id, k -> new StringBuilder()).append(delta);
        }
        void finishToolCall(String id, String name, String arguments) {
            toolCalls.add(new Content.ToolCallContent(id, name, arguments));
        }

        Message build() {
            var contents = new ArrayList<Content>();
            if (!text.isEmpty()) {
                contents.add(new Content.TextContent(text.toString()));
            }
            contents.addAll(toolCalls);

            if (contents.isEmpty() && errorMessage != null) {
                contents.add(new Content.TextContent("Error: " + errorMessage));
                stopReason = "error";
            }

            return new Message(Role.ASSISTANT, contents, stopReason, usage);
        }
    }
}
