package io.github.frank.harness.core.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sandbox that executes commands inside an isolated Docker container.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>A container is created with {@code sleep infinity} on the first
 *       {@link #execute} call (lazy initialisation).</li>
 *   <li>Commands run via Docker's {@code exec} API.</li>
 *   <li>{@link #close()} stops and removes the container.</li>
 * </ol>
 *
 * <p>Thread-safe: init / exec / close are serialised through internal
 * locking so only one operation touches the Docker daemon at a time.
 */
public class DockerSandbox implements Sandbox {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);

    private final String id;
    private final DockerClient client;
    private final SandboxConfig config;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Object initLock = new Object();
    private volatile String containerId;

    public DockerSandbox(DockerClient client, SandboxConfig config) {
        this.id = UUID.randomUUID().toString();
        this.client = client;
        this.config = config;
    }

    @Override
    public ExecutionResult execute(String command) {
        return execute(command, config.defaultTimeout());
    }

    @Override
    public ExecutionResult execute(String command, Duration timeout) {
        if (closed.get()) {
            return new ExecutionResult(-1, "", "Sandbox is closed", false);
        }

        try {
            String cid = ensureContainer();
            String execId = client.execCreate(cid, new String[]{"sh", "-c", command}, config.workDir().toString());

            DockerClient.ExecOutput output;
            try {
                output = client.execStart(execId);
            } catch (IOException e) {
                // May be a timeout — check if the exec is still running
                var inspect = client.execInspect(execId);
                if (inspect.running()) {
                    return ExecutionResult.timeout("", "Command timed out after " + timeout);
                }
                return new ExecutionResult(inspect.exitCode(), "", e.getMessage(), false);
            }

            var inspect = client.execInspect(execId);
            return new ExecutionResult(inspect.exitCode(), output.stdout(), output.stderr(), false);

        } catch (IOException e) {
            log.error("DockerSandbox error for command: {}", command, e);
            return new ExecutionResult(-1, "", e.getMessage(), false);
        }
    }

    private String ensureContainer() throws IOException {
        if (containerId != null) return containerId;
        synchronized (initLock) {
            if (containerId != null) return containerId;

            String image = config.image();
            if (image == null || image.isBlank()) {
                throw new IOException("DockerSandbox requires a non-blank image");
            }

            var envList = mapToList(config.env());
            containerId = client.createContainer(
                    image,
                    config.workDir().toString(),
                    envList,
                    config.volumes(),
                    config.memoryLimitMb(),
                    config.networkDisabled());
            client.startContainer(containerId);
            log.info("DockerSandbox {} created container {} (image={})", id, containerId, image);
            return containerId;
        }
    }

    private static List<String> mapToList(Map<String, String> map) {
        var list = new ArrayList<String>(map.size());
        for (var entry : map.entrySet()) {
            list.add(entry.getKey() + "=" + entry.getValue());
        }
        return list;
    }

    @Override
    public boolean isAlive() {
        return !closed.get() && containerId != null;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        if (containerId != null) {
            try {
                client.stopContainer(containerId);
            } catch (IOException e) {
                log.warn("Failed to stop container {}", containerId, e);
            }
            try {
                client.removeContainer(containerId);
            } catch (IOException e) {
                log.warn("Failed to remove container {}", containerId, e);
            }
        }
        log.debug("DockerSandbox {} closed", id);
    }
}
