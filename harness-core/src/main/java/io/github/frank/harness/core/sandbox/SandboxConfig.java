package io.github.frank.harness.core.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for creating a sandbox instance.
 *
 * @param image           Docker image name (null or blank = local sandbox)
 * @param workDir         working directory for command execution
 * @param env             environment variables to inject
 * @param defaultTimeout  default execution timeout (falls back to 30 s)
 * @param networkDisabled when true the sandbox has no network access
 * @param volumes         extra volume mounts (host:container)
 * @param memoryLimitMb   memory limit in megabytes (0 = unlimited)
 */
public record SandboxConfig(
    String image,
    Path workDir,
    Map<String, String> env,
    Duration defaultTimeout,
    boolean networkDisabled,
    List<String> volumes,
    long memoryLimitMb
) {
    public SandboxConfig {
        if (env == null) {
            env = Map.of();
        }
        if (defaultTimeout == null) {
            defaultTimeout = Duration.ofSeconds(30);
        }
        if (volumes == null) {
            volumes = List.of();
        }
        if (memoryLimitMb <= 0) {
            memoryLimitMb = 0;
        }
    }

    /** Convenience factory for a local sandbox. */
    public static SandboxConfig local(Path workDir) {
        return new SandboxConfig(null, workDir, Map.of(),
                Duration.ofSeconds(30), false, List.of(), 0);
    }

    /** Convenience factory for a Docker sandbox. */
    public static SandboxConfig docker(String image, Path workDir) {
        return new SandboxConfig(image, workDir, Map.of(),
                Duration.ofSeconds(30), true, List.of(), 512);
    }

    /** @return true when a Docker image is configured. */
    public boolean isDocker() {
        return image != null && !image.isBlank();
    }
}
