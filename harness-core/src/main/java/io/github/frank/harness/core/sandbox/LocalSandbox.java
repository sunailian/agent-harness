package io.github.frank.harness.core.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sandbox that runs commands directly on the host using {@link ProcessBuilder}.
 *
 * <p>Suitable for trusted workloads during development. For untrusted code
 * execution prefer {@link DockerSandbox}.
 */
public class LocalSandbox implements Sandbox {

    private static final Logger log = LoggerFactory.getLogger(LocalSandbox.class);

    private final String id;
    private final Path workDir;
    private final Map<String, String> env;
    private final Duration defaultTimeout;
    private volatile boolean closed;

    public LocalSandbox(Path workDir, Map<String, String> env, Duration defaultTimeout) {
        this.id = UUID.randomUUID().toString();
        this.workDir = workDir;
        this.env = Map.copyOf(env);
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public ExecutionResult execute(String command) {
        return execute(command, defaultTimeout);
    }

    @Override
    public ExecutionResult execute(String command, Duration timeout) {
        if (closed) {
            return new ExecutionResult(-1, "", "Sandbox is closed", false);
        }

        try {
            var pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(workDir.toFile());
            pb.environment().putAll(env);

            Process proc = pb.start();

            // Read stdout / stderr concurrently to avoid deadlocks
            var outFuture = CompletableFuture.supplyAsync(() -> readQuietly(proc.getInputStream()));
            var errFuture = CompletableFuture.supplyAsync(() -> readQuietly(proc.getErrorStream()));

            boolean finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                proc.destroyForcibly();
                var out = outFuture.getNow("");
                var err = errFuture.getNow("");
                return ExecutionResult.timeout(out, err);
            }

            String out = outFuture.join();
            String err = errFuture.join();

            return ExecutionResult.of(proc.exitValue(), out, err);

        } catch (IOException e) {
            log.error("LocalSandbox I/O error for command: {}", command, e);
            return new ExecutionResult(-1, "", e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(-1, "", "Interrupted", false);
        }
    }

    private static String readQuietly(java.io.InputStream stream) {
        try {
            var buf = new ByteArrayOutputStream();
            stream.transferTo(buf);
            return buf.toString();
        } catch (IOException e) {
            return "<stream error: " + e.getMessage() + ">";
        }
    }

    @Override
    public boolean isAlive() {
        return !closed;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void close() {
        closed = true;
        log.debug("LocalSandbox {} closed", id);
    }
}
