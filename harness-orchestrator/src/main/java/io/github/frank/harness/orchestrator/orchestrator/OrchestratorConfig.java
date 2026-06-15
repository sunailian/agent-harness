package io.github.frank.harness.orchestrator.orchestrator;

import java.time.Duration;

public record OrchestratorConfig(
    int maxConcurrency,
    Duration perWorkerTimeout,
    int maxWorkerTurns,
    int maxRetries
) {
    public static final OrchestratorConfig DEFAULT = new OrchestratorConfig(3, Duration.ofSeconds(300), 10, 1);
    public OrchestratorConfig {
        if (maxConcurrency < 1) maxConcurrency = 1;
        if (maxRetries < 0) maxRetries = 0;
    }
}
