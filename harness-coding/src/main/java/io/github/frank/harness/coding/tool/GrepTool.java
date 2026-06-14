package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepTool {
    private GrepTool() {}

    public static Tool create(Path workdir) {
        return new Tool("grep", "Search file contents with regex.",
            new JsonSchema("object", Map.of(
                "pattern", new JsonSchema.PropertyDef("string", "Regex pattern", null),
                "path", new JsonSchema.PropertyDef("string", "Directory/file to search", "."),
                "glob", new JsonSchema.PropertyDef("string", "File pattern filter (e.g. *.java)", null),
                "limit", new JsonSchema.PropertyDef("integer", "Max results", 50)
            ), List.of("pattern")),
            args -> {
                try {
                    Pattern regex;
                    try { regex = Pattern.compile(args.get("pattern").asText()); }
                    catch (PatternSyntaxException e) { return ToolResult.error("Invalid regex: " + e.getMessage()); }

                    Path root = ReadTool.resolve(workdir, args.has("path") ? args.get("path").asText() : ".");
                    int limit = args.has("limit") ? args.get("limit").asInt(50) : 50;
                    String glob = args.has("glob") ? args.get("glob").asText() : null;

                    var results = new StringBuilder();
                    try (Stream<Path> stream = Files.walk(root, 5)) {
                        var files = stream.filter(Files::isRegularFile)
                            .filter(f -> glob == null || f.getFileName().toString().contains(glob.replace("*", "")))
                            .toList();

                        int count = 0;
                        for (Path f : files) {
                            if (count >= limit) break;
                            try {
                                var lines = Files.readAllLines(f);
                                for (int i = 0; i < lines.size() && count < limit; i++) {
                                    if (regex.matcher(lines.get(i)).find()) {
                                        results.append(String.format("%s:%d: %s\n",
                                            workdir.relativize(f), i + 1, lines.get(i).strip()));
                                        count++;
                                    }
                                }
                            } catch (IOException ignored) {}
                        }
                    }
                    return ToolResult.success(results.isEmpty() ? "No matches" : results.toString());
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            });
    }
}
