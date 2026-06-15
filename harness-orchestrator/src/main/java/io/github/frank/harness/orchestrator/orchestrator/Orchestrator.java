package io.github.frank.harness.orchestrator.orchestrator;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.orchestrator.common.OrchestratorResult;
import io.github.frank.harness.orchestrator.common.WorkerResult;
import io.github.frank.harness.orchestrator.orchestrator.decompose.DecompositionStrategy;
import io.github.frank.harness.orchestrator.orchestrator.decompose.SubTask;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.SynthesisStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Orchestrator {
    private final OrchestratorConfig config;
    private final DecompositionStrategy decomposer;
    private final SynthesisStrategy synthesizer;
    private final ModelProvider provider;

    public Orchestrator(OrchestratorConfig config, DecompositionStrategy decomposer,
                         SynthesisStrategy synthesizer, ModelProvider provider) {
        this.config = config;
        this.decomposer = decomposer;
        this.synthesizer = synthesizer;
        this.provider = provider;
    }

    public OrchestratorResult execute(String goal) {
        var tasks = decomposer.decompose(goal);
        if (tasks.isEmpty()) {
            return OrchestratorResult.of("No subtasks generated.", List.of());
        }
        var results = dispatch(tasks);
        String summary = synthesizer.synthesize(goal, results);
        return OrchestratorResult.of(summary, results);
    }

    private List<WorkerResult> dispatch(List<SubTask> tasks) {
        int n = Math.min(config.maxConcurrency(), tasks.size());
        var executor = Executors.newFixedThreadPool(n);
        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> executeWorker(task), executor));
        }
        List<WorkerResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(config.perWorkerTimeout().toMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                results.add(WorkerResult.timeout(tasks.get(i).id()));
            } catch (Exception e) {
                results.add(WorkerResult.error(tasks.get(i).id(), e.getMessage()));
            }
        }
        executor.shutdownNow();
        return results;
    }

    private WorkerResult executeWorker(SubTask task) {
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                var spec = task.spec();
                if (spec == null) {
                    return WorkerResult.error(task.id(), "WorkerSpec is null");
                }
                var agent = Agent.builder()
                    .tools(spec.tools())
                    .config(spec.modelOverride() != null ? spec.modelOverride() : ModelConfig.of("gpt-4o"))
                    .sandbox(spec.sandbox())
                    .build();
                if (spec.systemPrompt() != null && !spec.systemPrompt().isBlank()) {
                    agent.setSystemPrompt(spec.systemPrompt());
                }
                agent.prompt(task.description());
                var loop = new AgentLoop(provider);
                var outputRef = new String[]{""};
                loop.run(agent, event -> {
                    if (event instanceof AgentLifecycleEvent.TurnComplete tc) {
                        outputRef[0] = tc.assistantMessage().contents().stream()
                            .filter(c -> c instanceof Content.TextContent)
                            .map(c -> ((Content.TextContent) c).text())
                            .collect(Collectors.joining());
                    }
                    if (event instanceof AgentLifecycleEvent.ErrorEvent ee) {
                        outputRef[0] = "Error: " + ee.message();
                    }
                });
                return WorkerResult.success(task.id(), outputRef[0]);
            } catch (Exception e) {
                if (attempt == config.maxRetries()) {
                    return WorkerResult.error(task.id(), e.getMessage());
                }
            }
        }
        return WorkerResult.error(task.id(), "Max retries exceeded");
    }
}
