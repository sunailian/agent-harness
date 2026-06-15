package io.github.frank.harness.core.sandbox;

import java.time.Duration;

/**
 * Sandbox — isolated command execution environment.
 *
 * <p>Implementations may run commands locally (LocalSandbox) or inside
 * Docker containers (DockerSandbox). The sandbox contract guarantees that
 * commands are executed with the configured working directory, environment
 * variables, and resource limits.
 */
public interface Sandbox extends AutoCloseable {

    /** Execute a command with the default timeout. */
    ExecutionResult execute(String command);

    /** Execute a command with an explicit timeout. */
    ExecutionResult execute(String command, Duration timeout);

    /** Whether the sandbox is still alive and accepting commands. */
    boolean isAlive();

    /** Unique identifier for this sandbox instance. */
    String id();

    /** Release resources held by this sandbox. */
    @Override
    void close();

    /**
     * Result of a sandbox command execution.
     */
    record ExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {

        public boolean isSuccess() {
            return exitCode == 0 && !timedOut;
        }

        public static ExecutionResult of(int exitCode, String stdout, String stderr) {
            return new ExecutionResult(exitCode, stdout, stderr, false);
        }

        public static ExecutionResult timeout(String stdout, String stderr) {
            return new ExecutionResult(-1, stdout, stderr, true);
        }
    }
}
