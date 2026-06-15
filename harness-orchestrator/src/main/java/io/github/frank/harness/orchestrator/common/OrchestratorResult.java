package io.github.frank.harness.orchestrator.common;

import java.util.List;

public record OrchestratorResult(
    String summary,
    List<WorkerResult> workerResults,
    boolean allSucceeded
) {
    public static OrchestratorResult of(String summary, List<WorkerResult> results) {
        boolean allOk = results.stream().allMatch(WorkerResult::success);
        return new OrchestratorResult(summary, results, allOk);
    }
}
