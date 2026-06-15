package io.github.frank.harness.orchestrator.orchestrator.decompose;
import java.util.List;

@FunctionalInterface
public interface DecompositionStrategy {
    List<SubTask> decompose(String goal);
}
