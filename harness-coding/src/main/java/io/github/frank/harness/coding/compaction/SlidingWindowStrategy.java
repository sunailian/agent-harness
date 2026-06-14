package io.github.frank.harness.coding.compaction;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.core.memory.CompactionStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * Sliding window compaction — keeps the N most recent messages.
 */
public class SlidingWindowStrategy implements CompactionStrategy {
    @Override
    public String name() { return "sliding-window"; }

    @Override
    public List<Message> compact(List<Message> history, int targetTokens) {
        int tokens = 0;
        var result = new ArrayList<Message>();
        for (int i = history.size() - 1; i >= 0 && tokens < targetTokens; i--) {
            var msg = history.get(i);
            int estimated = estimateTokens(msg);
            if (tokens + estimated > targetTokens && !result.isEmpty()) break;
            result.add(0, msg);
            tokens += estimated;
        }
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
