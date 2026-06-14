package io.github.frank.harness.coding.cli;

import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.provider.openai.OpenAIProvider;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.coding.compaction.SlidingWindowStrategy;
import io.github.frank.harness.coding.resource.ResourceLoader;
import io.github.frank.harness.coding.session.AgentSession;
import io.github.frank.harness.coding.session.JsonlSessionStore;
import io.github.frank.harness.coding.tool.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * CLI entry point for the Coding Agent.
 */
public class HarnessCLI {

    public static void main(String[] args) throws IOException {
        var workdir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }

        // Assemble dependencies
        ModelProvider provider = new OpenAIProvider(apiKey);
        var tools = List.of(
            ReadTool.create(workdir),
            WriteTool.create(workdir),
            BashTool.create(workdir, Duration.ofMinutes(2)),
            GrepTool.create(workdir),
            FindTool.create(workdir),
            LsTool.create(workdir),
            PatchTool.create(workdir)
        );

        var agent = Agent.builder()
            .tools(tools)
            .config(ModelConfig.of("gpt-4o"))
            .build();

        var resources = new ResourceLoader(workdir);
        var rules = resources.loadProjectRules();
        if (!rules.isEmpty()) {
            agent.setSystemPrompt("You are a coding agent. " + rules +
                "\nWork in: " + workdir);
        }

        var loop = new AgentLoop(provider);
        var store = new JsonlSessionStore(
            workdir.resolve(".harness/sessions"), UUID.randomUUID().toString());
        var compaction = new SlidingWindowStrategy();
        var session = new AgentSession(UUID.randomUUID().toString(), agent, loop, store, compaction);

        // Interactive REPL
        var scanner = new Scanner(System.in);
        System.out.println("🤖 Harness Coding Agent ready. Workdir: " + workdir);
        System.out.println("Type /exit to quit, /steer <text> to redirect.\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if ("/exit".equals(input)) break;
            if (input.startsWith("/steer ")) {
                session.steer(input.substring(7));
                continue;
            }

            System.out.println();
            session.prompt(input, event -> {
                switch (event) {
                    case AgentLifecycleEvent.TextDelta td -> System.out.print(td.text());
                    case AgentLifecycleEvent.ToolCallStart ts -> 
                        System.out.print("\n🔧 " + ts.call().name() + "... ");
                    case AgentLifecycleEvent.ToolCallEnd te ->
                        System.out.print("✓");
                    case AgentLifecycleEvent.TurnComplete tc ->
                        System.out.println("\n--- turn complete ---");
                    case AgentLifecycleEvent.ErrorEvent ee ->
                        System.err.println("\n❌ " + ee.message());
                    default -> {}
                }
            });
            System.out.println();
        }
        System.out.println("👋 Goodbye.");
    }
}
