package io.github.frank.harness.core.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provider that creates {@link DockerSandbox} instances.
 *
 * <p>Auto-detects the Docker socket at {@code /var/run/docker.sock}.
 * Set the system property {@code docker.socket} to override.
 */
public class DockerSandboxProvider implements SandboxProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxProvider.class);

    private static final String DEFAULT_SOCKET = "/var/run/docker.sock";
    private static final String SOCKET_PROPERTY = "docker.socket";

    private final String socketPath;
    private volatile DockerClient cachedClient;

    public DockerSandboxProvider() {
        this(System.getProperty(SOCKET_PROPERTY, DEFAULT_SOCKET));
    }

    public DockerSandboxProvider(String socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public Sandbox create(SandboxConfig config) {
        DockerClient client = cachedClient;
        if (client == null) {
            synchronized (this) {
                if (cachedClient == null) {
                    cachedClient = new DockerClient(socketPath);
                }
                client = cachedClient;
            }
        }
        return new DockerSandbox(client, config);
    }

    @Override
    public boolean supports(SandboxConfig config) {
        if (!config.isDocker()) {
            return false;
        }
        return Files.exists(Path.of(socketPath));
    }

    /** Path to the Docker socket this provider uses. */
    public String socketPath() {
        return socketPath;
    }
}
