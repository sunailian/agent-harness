package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class LsTool {
    private LsTool() {}

    public static Tool create(Path workdir) {
        return new Tool("ls", "List directory contents.",
            new JsonSchema("object", Map.of(
                "path", new JsonSchema.PropertyDef("string", "Directory path", ".")
            ), List.of()),
            args -> {
                try {
                    Path dir = ReadTool.resolve(workdir, args.has("path") ? args.get("path").asText() : ".");
                    if (!Files.isDirectory(dir)) return ToolResult.error("Not a directory: " + dir);
                    var sb = new StringBuilder();
                    try (var stream = Files.list(dir)) {
                        stream.sorted().forEach(f -> {
                            String type = Files.isDirectory(f) ? "d" : "f";
                            sb.append(String.format("%s %s\n", type, f.getFileName()));
                        });
                    }
                    return ToolResult.success(sb.isEmpty() ? "(empty)" : sb.toString());
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
