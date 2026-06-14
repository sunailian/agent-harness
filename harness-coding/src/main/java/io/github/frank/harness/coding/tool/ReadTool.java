package io.github.frank.harness.coding.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ReadTool {
    private ReadTool() {}

    public static Tool create(Path workdir) {
        return new Tool("read", "Read a file from the filesystem.",
            new JsonSchema("object", Map.of(
                "path", new JsonSchema.PropertyDef("string", "Absolute or relative path", null),
                "offset", new JsonSchema.PropertyDef("integer", "Line number to start from (1-indexed)", 1),
                "limit", new JsonSchema.PropertyDef("integer", "Max lines to read", 500)
            ), List.of("path")),
            args -> {
                try {
                    Path p = resolve(workdir, args.get("path").asText());
                    if (!Files.isRegularFile(p)) return ToolResult.error("Not a file: " + p);
                    String content = Files.readString(p);
                    int offset = args.has("offset") ? args.get("offset").asInt(1) - 1 : 0;
                    int limit = args.has("limit") ? args.get("limit").asInt(500) : 500;
                    String[] lines = content.split("\n", -1);
                    if (offset >= lines.length) return ToolResult.success("(offset beyond file end)");
                    int end = Math.min(offset + limit, lines.length);
                    var sb = new StringBuilder();
                    for (int i = offset; i < end; i++) {
                        sb.append(String.format("%5d|%s\n", i + 1, lines[i]));
                    }
                    return ToolResult.success(sb.toString());
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }

    static Path resolve(Path workdir, String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : workdir.resolve(p).normalize();
    }
}
