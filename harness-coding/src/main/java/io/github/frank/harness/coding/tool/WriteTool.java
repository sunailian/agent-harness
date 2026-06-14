package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class WriteTool {
    private WriteTool() {}

    public static Tool create(Path workdir) {
        return new Tool("write", "Write or overwrite a file.",
            new JsonSchema("object", Map.of(
                "path", new JsonSchema.PropertyDef("string", "Target file path", null),
                "content", new JsonSchema.PropertyDef("string", "Content to write", null)
            ), List.of("path", "content")),
            args -> {
                try {
                    Path p = ReadTool.resolve(workdir, args.get("path").asText());
                    Files.createDirectories(p.getParent());
                    Files.writeString(p, args.get("content").asText());
                    return ToolResult.success("Written: " + p);
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
