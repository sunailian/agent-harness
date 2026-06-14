package io.github.frank.harness.coding.session;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSONL session persistence — one JSON message per line, append-only.
 */
public class JsonlSessionStore {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Path sessionFile;

    public JsonlSessionStore(Path sessionsDir, String sessionId) throws IOException {
        Files.createDirectories(sessionsDir);
        this.sessionFile = sessionsDir.resolve(sessionId + ".jsonl");
    }

    public void appendMessage(Message msg) {
        try {
            var json = mapper.createObjectNode();
            json.put("role", msg.role().name().toLowerCase());
            if (msg.stopReason() != null) json.put("stopReason", msg.stopReason());
            var contents = json.putArray("contents");
            for (var c : msg.contents()) {
                var cn = contents.addObject();
                switch (c) {
                    case Content.TextContent t -> { cn.put("type", "text"); cn.put("text", t.text()); }
                    case Content.ToolCallContent tc -> {
                        cn.put("type", "toolCall"); cn.put("id", tc.id());
                        cn.put("name", tc.name()); cn.put("arguments", tc.arguments());
                    }
                    case Content.ToolResultContent tr -> {
                        cn.put("type", "toolResult"); cn.put("callId", tr.callId());
                        cn.put("name", tr.name()); cn.put("output", tr.output());
                        cn.put("isError", tr.isError());
                    }
                    case Content.ThinkContent th -> { cn.put("type", "think"); cn.put("reasoning", th.reasoning()); }
                }
            }
            Files.writeString(sessionFile, mapper.writeValueAsString(json) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write session", e);
        }
    }

    public List<Message> loadAll() throws IOException {
        if (!Files.exists(sessionFile)) return List.of();
        var messages = new ArrayList<Message>();
        for (String line : Files.readAllLines(sessionFile)) {
            if (line.isBlank()) continue;
            try {
                var node = mapper.readTree(line);
                var role = Role.valueOf(node.get("role").asText().toUpperCase());
                var contents = new ArrayList<Content>();
                for (var cn : node.get("contents")) {
                    String type = cn.get("type").asText();
                    switch (type) {
                        case "text" -> contents.add(new Content.TextContent(cn.get("text").asText()));
                        case "toolCall" -> contents.add(new Content.ToolCallContent(
                            cn.get("id").asText(), cn.get("name").asText(), cn.get("arguments").asText()));
                        case "toolResult" -> contents.add(new Content.ToolResultContent(
                            cn.get("callId").asText(), cn.get("name").asText(),
                            cn.get("output").asText(), cn.get("isError").asBoolean()));
                        case "think" -> contents.add(new Content.ThinkContent(cn.get("reasoning").asText()));
                    }
                }
                messages.add(new Message(role, contents, 
                    node.has("stopReason") ? node.get("stopReason").asText() : null, Map.of()));
            } catch (Exception e) {
                // skip corrupted lines
            }
        }
        return messages;
    }

    public Path getFile() { return sessionFile; }
}
