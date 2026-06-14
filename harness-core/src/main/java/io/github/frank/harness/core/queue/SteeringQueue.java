package io.github.frank.harness.core.queue;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Steering queue — user mid-turn direction changes.
 * Messages are consumed before the next model request.
 */
public class SteeringQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void push(String text) {
        queue.add(text);
    }

    /** Drain all queued steering messages into a single string. */
    public String drain() {
        var sb = new StringBuilder();
        String item;
        while ((item = queue.poll()) != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(item);
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
