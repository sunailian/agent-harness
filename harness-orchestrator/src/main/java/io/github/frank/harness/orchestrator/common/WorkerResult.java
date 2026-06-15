package io.github.frank.harness.orchestrator.common;

public record WorkerResult(
    String subtaskId,
    boolean success,
    String output,
    int turnsTaken,
    String errorMessage
) {
    public static WorkerResult success(String subtaskId, String output) {
        return new WorkerResult(subtaskId, true, output, 0, null);
    }
    public static WorkerResult timeout(String subtaskId) {
        return new WorkerResult(subtaskId, false, "", 0, "Timed out");
    }
    public static WorkerResult error(String subtaskId, String message) {
        return new WorkerResult(subtaskId, false, "", 0, message);
    }
}
