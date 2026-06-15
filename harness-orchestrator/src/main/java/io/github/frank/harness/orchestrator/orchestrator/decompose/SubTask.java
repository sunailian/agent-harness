package io.github.frank.harness.orchestrator.orchestrator.decompose;

import io.github.frank.harness.orchestrator.common.WorkerSpec;
import java.util.List;
import java.util.UUID;

public record SubTask(
    String id,
    String description,
    WorkerSpec spec,
    List<String> dependsOn
) {
    public SubTask {
        if (dependsOn == null) dependsOn = List.of();
    }
    public static SubTask of(String description, WorkerSpec spec) {
        return new SubTask(UUID.randomUUID().toString(), description, spec, List.of());
    }
}
