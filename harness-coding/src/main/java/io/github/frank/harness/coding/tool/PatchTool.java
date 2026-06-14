package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PatchTool {
    private PatchTool() {}

    public static Tool create(Path workdir) {
        return new Tool("patch", "Find and replace text in a file.",
            new JsonSchema("object", Map.of(
                "path", new JsonSchema.PropertyDef("string", "File path", null),
                "old_string", new JsonSchema.PropertyDef("string", "Text to find", null),
                "new_string", new JsonSchema.PropertyDef("string", "Replacement text", null)
            ), List.of("path", "old_string", "new_string")),
            args -> {
                try {
                    Path p = ReadTool.resolve(workdir, args.get("path").asText());
                    if (!Files.isRegularFile(p)) return ToolResult.error("Not a file: " + p);
                    String content = Files.readString(p);
                    String old = args.get("old_string").asText();
                    String replacement = args.has("new_string") ? args.get("new_string").asText() : "";
                    if (!content.contains(old)) return ToolResult.error("old_string not found in file");
                    String updated = content.replace(old, replacement);
                    Files.writeString(p, updated);
                    return ToolResult.success("Patched: " + p);
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
