package io.github.frank.harness.core.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSandboxTest {
    @TempDir Path tempDir;
    private LocalSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new LocalSandbox(tempDir, Map.of(), Duration.ofSeconds(30));
    }

    @Test void executeSimpleCommand() {
        var result = sandbox.execute("echo hello");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).contains("hello");
    }

    @Test void executeWithNonZeroExit() {
        var result = sandbox.execute("exit 1");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test void executeTimeout() {
        var result = sandbox.execute("sleep 10", Duration.ofMillis(100));
        assertThat(result.timedOut()).isTrue();
    }

    @Test void isAliveBeforeClose() {
        assertThat(sandbox.isAlive()).isTrue();
    }

    @Test void isAliveAfterClose() {
        sandbox.close();
        assertThat(sandbox.isAlive()).isFalse();
    }

    @Test void executeAfterCloseReturnsError() {
        sandbox.close();
        var result = sandbox.execute("echo hi");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.stderr()).contains("closed");
    }

    @Test void hasUniqueId() {
        var other = new LocalSandbox(tempDir, Map.of(), Duration.ofSeconds(30));
        assertThat(sandbox.id()).isNotEqualTo(other.id());
    }
}
