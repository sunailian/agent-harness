package io.github.frank.harness.core.approval;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.ToolResult;
import io.github.frank.harness.core.hook.ToolHook;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class ApprovalHook implements ToolHook {
    private final List<ApprovalPattern> patterns;
    private final ConcurrentLinkedQueue<ApprovalRequest> pending;
    private final Duration approvalTimeout;

    public ApprovalHook(List<ApprovalPattern> patterns, Duration approvalTimeout) {
        this.patterns = List.copyOf(patterns);
        this.pending = new ConcurrentLinkedQueue<>();
        this.approvalTimeout = approvalTimeout;
    }

    @Override
    public boolean beforeToolCall(Content.ToolCallContent call, JsonNode args) {
        if (patterns.isEmpty()) return true;

        String command = extractCommand(call, args);
        if (command == null || command.isBlank()) return true;

        for (var p : patterns) {
            if (p.matches(command)) {
                var req = ApprovalRequest.create(
                    call.name(), sanitize(command), p);
                pending.add(req);

                try {
                    return req.decision().get(
                        approvalTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (ExecutionException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ToolResult afterToolCall(Content.ToolCallContent call, ToolResult result) {
        return result;
    }

    public ApprovalRequest pollPending() {
        return pending.poll();
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    private String extractCommand(Content.ToolCallContent call, JsonNode args) {
        return args.has("command") ? args.get("command").asText() : null;
    }

    private String sanitize(String command) {
        return command
            .replaceAll("(?:--api-key|--token|--password)\\s+\\S+", "$1 ***");
    }
}
