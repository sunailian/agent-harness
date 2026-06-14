package io.github.frank.harness.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-based implementation of MemoryStore.
 * <p>
 * Each key is stored as a JSON file under the store directory.
 * Phase 1: simple text-match search. Phase 3: upgrade to vector search.
 */
public class FileSystemMemoryStore implements MemoryStore {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Path dir;

    public FileSystemMemoryStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
    }

    @Override
    public void save(String key, String value) {
        try {
            var node = mapper.createObjectNode();
            node.put("key", key);
            node.put("value", value);
            node.put("timestamp", System.currentTimeMillis());
            Path file = fileFor(key);
            Files.writeString(file, mapper.writeValueAsString(node));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save memory: " + key, e);
        }
    }

    @Override
    public Optional<String> load(String key) {
        Path file = fileFor(key);
        if (!Files.exists(file)) return Optional.empty();
        try {
            var node = mapper.readTree(Files.readString(file));
            return Optional.of(node.get("value").asText());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> search(String query, int limit) {
        var results = new ArrayList<String>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                .forEach(f -> {
                    try {
                        var content = Files.readString(f);
                        if (content.toLowerCase().contains(query.toLowerCase())) {
                            var node = mapper.readTree(content);
                            results.add(node.get("value").asText());
                        }
                    } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
        return results.stream().limit(limit).toList();
    }

    private Path fileFor(String key) {
        // Sanitize key to safe filename
        String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return dir.resolve(safe + ".json");
    }
}
