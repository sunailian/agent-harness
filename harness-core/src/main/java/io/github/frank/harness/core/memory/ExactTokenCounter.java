package io.github.frank.harness.core.memory;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * Token-based context window — keeps system prompt + fits remaining messages.
 */
public class ExactTokenCounter implements ContextWindow {

    @Override
    public List<Message> fit(List<Message> fullHistory, int maxTokens) {
        // Always keep system message
        Message system = null;
        var others = new ArrayList<Message>();
        for (var msg : fullHistory) {
            if (msg.role() == Role.SYSTEM) system = msg;
            else others.add(msg);
        }

        var result = new ArrayList<Message>();
        if (system != null) {
            result.add(system);
            maxTokens -= estimateTokens(system);
        }

        // Sliding window from the end
        int tokens = 0;
        var window = new ArrayList<Message>();
        for (int i = others.size() - 1; i >= 0; i--) {
            var msg = others.get(i);
            int est = estimateTokens(msg);
            if (tokens + est > maxTokens && !window.isEmpty()) break;
            window.add(0, msg);
            tokens += est;
        }
        result.addAll(window);
        return result;
    }

    private int estimateTokens(Message msg) {
        int chars = 0;
        for (var c : msg.contents()) {
            if (c instanceof Content.TextContent t) chars += t.text().length();
            else if (c instanceof Content.ToolCallContent tc) chars += tc.arguments().length() + 20;
            else if (c instanceof Content.ToolResultContent tr) chars += tr.output().length();
            else chars += 50;
        }
        return Math.max(1, chars / 4);
    }
}
