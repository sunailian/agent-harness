package io.github.frank.harness.core.queue;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Follow-up queue — tasks to run after the current turn completes.
 */
public class FollowUpQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void push(String text) {
        queue.add(text);
    }

    /** Poll one follow-up message. Returns null if empty. */
    public String poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
