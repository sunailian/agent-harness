package io.github.frank.harness.core.approval;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalHookTest {
    private ApprovalHook hook;
    private JsonNodeFactory jnf;

    @BeforeEach void setUp() {
        jnf = JsonNodeFactory.instance;
        var patterns = List.of(
            ApprovalPattern.of("file_destruction", "Delete files", "rm\\s+(-rf|--no-preserve-root)"),
            ApprovalPattern.of("priv", "Privilege escalation", "sudo\\s+")
        );
        hook = new ApprovalHook(patterns, Duration.ofSeconds(2));
    }

    @Test void safeCommandPasses() {
        var call = new Content.ToolCallContent("c1", "bash", "{}");
        var args = jnf.objectNode().put("command", "echo hello");
        assertThat(hook.beforeToolCall(call, args)).isTrue();
        assertThat(hook.hasPending()).isFalse();
    }

    @Test void dangerousCommandCreatesPendingAndBlocks() throws Exception {
        var call = new Content.ToolCallContent("c2", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm -rf /tmp/foo");
        var future = new CompletableFuture<Boolean>();
        new Thread(() -> future.complete(hook.beforeToolCall(call, args))).start();
        Thread.sleep(150);
        assertThat(hook.hasPending()).isTrue();
        var req = hook.pollPending();
        assertThat(req).isNotNull();
        assertThat(req.riskType()).isEqualTo("file_destruction");
        req.decision().complete(true);
        assertThat(future.get()).isTrue();
    }

    @Test void userRejectsBlocksExecution() throws Exception {
        var call = new Content.ToolCallContent("c3", "bash", "{}");
        var args = jnf.objectNode().put("command", "sudo rm -rf /");
        var future = new CompletableFuture<Boolean>();
        new Thread(() -> future.complete(hook.beforeToolCall(call, args))).start();
        Thread.sleep(150);
        hook.pollPending().decision().complete(false);
        assertThat(future.get()).isFalse();
    }

    @Test void timeoutAutoRejects() {
        var quickHook = new ApprovalHook(
            List.of(ApprovalPattern.of("test", "test", "rm.*")),
            Duration.ofMillis(200));
        var call = new Content.ToolCallContent("c4", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm file.txt");
        assertThat(quickHook.beforeToolCall(call, args)).isFalse();
    }

    @Test void emptyPatternsPassAlways() {
        var noop = new ApprovalHook(List.of(), Duration.ofSeconds(1));
        var call = new Content.ToolCallContent("c5", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm -rf /");
        assertThat(noop.beforeToolCall(call, args)).isTrue();
    }

    @Test void nullCommandPasses() {
        var call = new Content.ToolCallContent("c6", "bash", "{}");
        assertThat(hook.beforeToolCall(call, jnf.objectNode())).isTrue();
    }

    @Test void afterToolCallPassthrough() {
        var result = ToolResult.success("ok");
        var call = new Content.ToolCallContent("c7", "bash", "{}");
        assertThat(hook.afterToolCall(call, result)).isSameAs(result);
    }
}
