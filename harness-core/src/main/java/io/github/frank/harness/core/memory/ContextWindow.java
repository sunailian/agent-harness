package io.github.frank.harness.core.memory;

import io.github.frank.harness.ai.protocol.Message;

import java.util.List;

/**
 * Context window manager — fits full history into token limit.
 */
public interface ContextWindow {
    List<Message> fit(List<Message> fullHistory, int maxTokens);
}
