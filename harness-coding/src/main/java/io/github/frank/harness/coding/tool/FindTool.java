package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FindTool {
    private FindTool() {}

    public static Tool create(Path workdir) {
        return new Tool("find", "Find files by name pattern.",
            new JsonSchema("object", Map.of(
                "pattern", new JsonSchema.PropertyDef("string", "Glob pattern (e.g. *.java)", null),
                "path", new JsonSchema.PropertyDef("string", "Search root", ".")
            ), List.of("pattern")),
            args -> {
                try {
                    Path root = ReadTool.resolve(workdir, args.has("path") ? args.get("path").asText() : ".");
                    String pattern = args.get("pattern").asText();
                    var results = new StringBuilder();
                    try (var stream = Files.walk(root, 10)) {
                        stream.filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().contains(
                                pattern.replace("*", "").replace("?", "")))
                            .limit(100)
                            .forEach(f -> results.append(workdir.relativize(f)).append("\n"));
                    }
                    return ToolResult.success(results.isEmpty() ? "No matches" : results.toString());
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
