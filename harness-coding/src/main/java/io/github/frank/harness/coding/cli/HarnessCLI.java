package io.github.frank.harness.coding.cli;

import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.provider.openai.OpenAIProvider;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.approval.ApprovalHook;
import io.github.frank.harness.core.approval.ApprovalPattern;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.core.sandbox.*;
import io.github.frank.harness.coding.compaction.SlidingWindowStrategy;
import io.github.frank.harness.coding.resource.ResourceLoader;
import io.github.frank.harness.coding.session.AgentSession;
import io.github.frank.harness.coding.session.JsonlSessionStore;
import io.github.frank.harness.coding.tool.*;
import io.github.frank.harness.orchestrator.common.WorkerSpec;
import io.github.frank.harness.orchestrator.orchestrator.Orchestrator;
import io.github.frank.harness.orchestrator.orchestrator.OrchestratorConfig;
import io.github.frank.harness.orchestrator.orchestrator.decompose.LlmDecomposer;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.LlmSynthesizer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;

public class HarnessCLI {

    public static void main(String[] args) throws IOException {
        var workdir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }

        ModelProvider modelProvider = new OpenAIProvider(apiKey);

        // ── Sandbox setup ──────────────────────────────────
        String sandboxMode = System.getenv().getOrDefault("HARNESS_SANDBOX", "local");
        SandboxConfig sandboxConfig;
        Sandbox sandbox;
        if ("docker".equalsIgnoreCase(sandboxMode)) {
            sandboxConfig = SandboxConfig.docker("ubuntu:22.04", workdir);
            var provider = new DockerSandboxProvider();
            sandbox = provider.create(sandboxConfig);
        } else {
            sandboxConfig = SandboxConfig.local(workdir);
            sandbox = new LocalSandbox(workdir, Map.of(), sandboxConfig.defaultTimeout());
        }

        // ── Approval setup ─────────────────────────────────
        List<ApprovalPattern> patterns = new ArrayList<>();
        patterns.add(ApprovalPattern.of("file_destruction", "删除文件/目录",
            "rm\\s+(-rf|--no-preserve-root)"));
        patterns.add(ApprovalPattern.of("privilege_escalation", "提权操作",
            "sudo\\s+|chmod\\s+777"));
        patterns.add(ApprovalPattern.of("git_force", "强制推送",
            "git\\s+push\\s+.*--force"));
        var approvalHook = new ApprovalHook(patterns, Duration.ofSeconds(30));

        // ── Tools assembly ─────────────────────────────────
        var tools = List.of(
            BashTool.create(sandbox, Duration.ofMinutes(2)),
            ReadTool.create(workdir),
            WriteTool.create(workdir),
            GrepTool.create(workdir),
            FindTool.create(workdir),
            LsTool.create(workdir),
            PatchTool.create(workdir)
        );

        var agent = Agent.builder()
            .tools(tools)
            .config(ModelConfig.of("gpt-4o"))
            .hooks(List.of(approvalHook))
            .sandbox(sandbox)
            .build();

        var resources = new ResourceLoader(workdir);
        var rules = resources.loadProjectRules();
        if (!rules.isEmpty()) {
            agent.setSystemPrompt("You are a coding agent. " + rules +
                "\nWork in: " + workdir);
        }

        var loop = new AgentLoop(modelProvider);
        var store = new JsonlSessionStore(
            workdir.resolve(".harness/sessions"), UUID.randomUUID().toString());
        var compaction = new SlidingWindowStrategy();
        var session = new AgentSession(UUID.randomUUID().toString(),
            agent, loop, store, compaction);

        // ── Shutdown hook ──────────────────────────────────
        Sandbox finalSandbox = sandbox;
        Runtime.getRuntime().addShutdownHook(new Thread(finalSandbox::close));

        // ── Orchestrator setup ─────────────────────────────
        var orchestratorTools = List.of(
            BashTool.create(sandbox, Duration.ofMinutes(2)),
            ReadTool.create(workdir),
            WriteTool.create(workdir),
            GrepTool.create(workdir),
            FindTool.create(workdir),
            LsTool.create(workdir),
            PatchTool.create(workdir)
        );
        var workerSpec = WorkerSpec.of("coder",
            "You are a coding agent. Complete the assigned task precisely. " +
            "Work in: " + workdir, orchestratorTools);
        var orchestrator = new Orchestrator(
            OrchestratorConfig.DEFAULT,
            new LlmDecomposer(modelProvider, ModelConfig.of("gpt-4o"), workerSpec),
            new LlmSynthesizer(modelProvider, ModelConfig.of("gpt-4o")),
            modelProvider
        );

        // ── Dual-thread REPL ───────────────────────────────
        var loopExecutor = Executors.newSingleThreadExecutor();
        var scanner = new Scanner(System.in);
        System.out.println("🤖 Harness Coding Agent ready. Workdir: " + workdir);
        System.out.println("   Sandbox: " + sandboxMode);
        System.out.println("   Approval: " + (!patterns.isEmpty() ? patterns.size() + " patterns" : "disabled"));
        System.out.println("Type /exit to quit, /steer <text> to redirect, /orchestrate <goal> for multi-agent.\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if ("/exit".equals(input)) break;
            if (input.startsWith("/steer ")) {
                session.steer(input.substring(7));
                continue;
            }
            if (input.startsWith("/orchestrate ")) {
                String goal = input.substring(13);
                System.out.println("\n🎯 Orchestrating: " + goal);
                var result = orchestrator.execute(goal);
                System.out.println(result.summary());
                for (var wr : result.workerResults()) {
                    String status = wr.success() ? "✓" : "✗";
                    String preview = wr.output().length() > 150
                        ? wr.output().substring(0, 150).replace("\n", " ") + "..."
                        : wr.output().replace("\n", " ");
                    System.out.printf("  [%s] %s%n", status, preview);
                }
                long ok = result.workerResults().stream()
                    .filter(io.github.frank.harness.orchestrator.common.WorkerResult::success).count();
                System.out.printf("  %d/%d subtasks succeeded.%n",
                    ok, result.workerResults().size());
                continue;
            }

            System.out.println();
            var loopFuture = loopExecutor.submit(() -> {
                session.prompt(input, event -> {
                    switch (event) {
                        case AgentLifecycleEvent.TextDelta td ->
                            System.out.print(td.text());
                        case AgentLifecycleEvent.ToolCallStart ts ->
                            System.out.print("\n🔧 " + ts.call().name() + "... ");
                        case AgentLifecycleEvent.ToolCallEnd te ->
                            System.out.print((te.result().success() ? "✓" : "✗"));
                        case AgentLifecycleEvent.TurnComplete tc ->
                            System.out.println("\n--- turn complete ---");
                        case AgentLifecycleEvent.ErrorEvent ee ->
                            System.err.println("\n❌ " + ee.message());
                        default -> {}
                    }
                });
            });

            // Main thread: poll approvals while loop runs
            while (!loopFuture.isDone()) {
                var approval = approvalHook.pollPending();
                if (approval != null) {
                    System.out.println();
                    System.out.println("⚠️  危险操作 [" + approval.toolName() +
                        "] — 需要审批");
                    System.out.println("   命令: " + approval.command());
                    System.out.println("   风险: " + approval.description());
                    System.out.print("   允许执行? (yes/no): ");
                    String answer = scanner.nextLine().trim();
                    approval.decision().complete("yes".equalsIgnoreCase(answer));
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println();
        }

        loopExecutor.shutdownNow();
        sandbox.close();
        System.out.println("👋 Goodbye.");
    }
}
