package io.github.frank.harness.core.memory;

import io.github.frank.harness.ai.protocol.Message;

import java.util.List;

/**
 * Compaction strategy — compresses conversation history to target token count.
 * Implementations: SlidingWindow, Summarize, Hybrid (Memory exploration Phases 2-5).
 */
public interface CompactionStrategy {
    String name();
    List<Message> compact(List<Message> history, int targetTokens);
}
