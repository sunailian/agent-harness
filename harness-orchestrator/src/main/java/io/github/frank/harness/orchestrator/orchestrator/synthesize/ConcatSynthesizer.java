package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import java.util.List;

public class ConcatSynthesizer implements SynthesisStrategy {
    @Override
    public String synthesize(String goal, List<WorkerResult> results) {
        var sb = new StringBuilder();
        sb.append("## Goal: ").append(goal).append("\n\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("### SubTask ").append(i + 1);
            if (!r.success()) {
                sb.append(" [FAILED: ").append(r.errorMessage()).append("]");
            }
            sb.append("\n").append(r.output()).append("\n\n");
        }
        long successCount = results.stream().filter(WorkerResult::success).count();
        sb.append("---\n**").append(successCount).append("/").append(results.size())
            .append("** subtasks succeeded.");
        return sb.toString();
    }
}
