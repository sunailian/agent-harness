package io.github.frank.harness.orchestrator.orchestrator.synthesize;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import java.util.List;

@FunctionalInterface
public interface SynthesisStrategy {
    String synthesize(String goal, List<WorkerResult> results);
}
