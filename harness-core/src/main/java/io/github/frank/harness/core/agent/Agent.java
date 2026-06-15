package io.github.frank.harness.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.stream.AssistantEvent;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.hook.HookChain;
import io.github.frank.harness.core.hook.ToolHook;
import io.github.frank.harness.core.queue.FollowUpQueue;
import io.github.frank.harness.core.queue.SteeringQueue;
import io.github.frank.harness.core.sandbox.Sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Agent — state machine holding context, tools, config, and queues.
 *
 * <p>Thread-safe via AtomicReference for state and ConcurrentLinkedQueue for
 * steering/follow-up messages. Designed to be driven by {@link io.github.frank.harness.core.loop.AgentLoop}.
 */
public class Agent {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<AgentState> state = new AtomicReference<>(AgentState.IDLE);
    private final List<Message> context = new ArrayList<>();
    private final List<Tool> tools;
    private final ModelConfig config;
    private final SteeringQueue steering = new SteeringQueue();
    private final FollowUpQueue followUp = new FollowUpQueue();
    private final HookChain hooks;

    /** Execution sandbox for isolated command execution. */
    private final Sandbox sandbox;

    private Message systemPrompt;

    public Agent(List<Tool> tools, ModelConfig config, List<ToolHook> hookList, Sandbox sandbox) {
        this.tools = List.copyOf(tools);
        this.config = config;
        this.hooks = new HookChain(hookList != null ? hookList : List.of());
        this.sandbox = sandbox;
    }

    // ── Public API ────────────────────────────────────────────

    public void setSystemPrompt(String text) {
        this.systemPrompt = Message.system(text);
    }

    public void prompt(String text) {
        context.add(Message.user(text));
    }

    public void steer(String text) {
        steering.push(text);
    }

    public void followUp(String text) {
        followUp.push(text);
    }

    public void abort() {
        state.set(AgentState.STOPPING);
    }

    // ── Public — used by AgentLoop ────────────────────────────

    public AgentState getState() { return state.get(); }
    public void setState(AgentState s) { state.set(s); }

    public List<Message> getContext() {
        var full = new ArrayList<Message>();
        if (systemPrompt != null) full.add(systemPrompt);
        full.addAll(context);
        return full;
    }

    public void addMessage(Message msg) { context.add(msg); }

    public List<Tool> getTools() { return tools; }
    public ModelConfig getConfig() { return config; }
    public SteeringQueue getSteering() { return steering; }
    public FollowUpQueue getFollowUp() { return followUp; }
    public HookChain getHooks() { return hooks; }
    public Sandbox getSandbox() { return sandbox; }

    // ── Tool helpers ──────────────────────────────────────────

    public Tool findTool(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    public JsonNode validateArgs(Tool tool, String arguments) {
        try {
            return mapper.readTree(arguments);
        } catch (Exception e) {
            return mapper.nullNode();
        }
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<Tool> tools = List.of();
        private ModelConfig config = ModelConfig.of("gpt-4o");
        private List<ToolHook> hooks = List.of();
        private Sandbox sandbox;

        public Builder tools(List<Tool> t) { this.tools = t; return this; }
        public Builder config(ModelConfig c) { this.config = c; return this; }
        public Builder hooks(List<ToolHook> h) { this.hooks = h; return this; }
        public Builder sandbox(Sandbox s) { this.sandbox = s; return this; }

        public Agent build() {
            return new Agent(tools, config, hooks, sandbox);
        }
    }
}
