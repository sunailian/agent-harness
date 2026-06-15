package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import io.github.frank.harness.core.sandbox.Sandbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class BashTool {
    private BashTool() {}

    public static Tool create(Sandbox sandbox, Duration timeout) {
        return new Tool("bash", "Execute a shell command in the sandbox.",
            new JsonSchema("object", Map.of(
                "command", new JsonSchema.PropertyDef("string",
                    "Shell command to execute (use '&&' to chain)", null)
            ), List.of("command")),
            args -> {
                var result = sandbox.execute(args.get("command").asText(), timeout);
                if (result.isSuccess()) {
                    String out = result.stdout();
                    return ToolResult.success(out.isEmpty() ? "(no output)" : out);
                }
                if (result.timedOut()) {
                    return ToolResult.error("Timed out after " + timeout.toSeconds() + "s");
                }
                String err = result.stderr();
                return ToolResult.error(err.isEmpty() ? "Exit code: " + result.exitCode() : err);
            });
    }
}
