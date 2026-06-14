package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BashTool {
    private BashTool() {}

    public static Tool create(Path workdir, Duration timeout) {
        return new Tool("bash", "Execute a shell command in the working directory.",
            new JsonSchema("object", Map.of(
                "command", new JsonSchema.PropertyDef("string", "Shell command to execute", null)
            ), List.of("command")),
            args -> {
                try {
                    var pb = new ProcessBuilder("bash", "-c", args.get("command").asText());
                    pb.directory(workdir.toFile());
                    pb.redirectErrorStream(true);
                    var p = pb.start();
                    boolean finished = p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
                    if (!finished) { p.destroyForcibly(); return ToolResult.error("Timed out"); }
                    String out = new String(p.getInputStream().readAllBytes());
                    return ToolResult.success(out.isEmpty() ? "(no output)" : out);
                } catch (Exception e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
