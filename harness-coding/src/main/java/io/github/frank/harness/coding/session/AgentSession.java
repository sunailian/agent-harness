package io.github.frank.harness.coding.session;

import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.agent.AgentState;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.core.memory.CompactionStrategy;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * AgentSession — bridges Agent + persistence + resource loading.
 */
public class AgentSession {
    private final String sessionId;
    private final Agent agent;
    private final AgentLoop loop;
    private final JsonlSessionStore store;
    private final CompactionStrategy compaction;

    public AgentSession(String sessionId, Agent agent, AgentLoop loop,
                        JsonlSessionStore store, CompactionStrategy compaction) {
        this.sessionId = sessionId;
        this.agent = agent;
        this.loop = loop;
        this.store = store;
        this.compaction = compaction;
    }

    public void prompt(String text, Consumer<AgentLifecycleEvent> onEvent) {
        // Inject project rules as system prompt
        agent.prompt(text);

        // Run loop — events are forwarded to both the callback and persisted
        loop.run(agent, event -> {
            onEvent.accept(event);
            // Persist key events
            if (event instanceof AgentLifecycleEvent.TurnComplete tc) {
                store.appendMessage(tc.assistantMessage());
            }
        });
    }

    public void steer(String text) { agent.steer(text); }
    public void followUp(String text) { agent.followUp(text); }
    public void abort() { agent.abort(); }

    public String getSessionId() { return sessionId; }
    public Agent getAgent() { return agent; }

    public void loadHistory() throws IOException {
        for (var msg : store.loadAll()) {
            // Re-populate context from saved messages
            agent.addMessage(msg);
        }
    }
}
