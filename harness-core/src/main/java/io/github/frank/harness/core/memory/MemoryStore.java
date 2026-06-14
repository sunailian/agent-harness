package io.github.frank.harness.core.memory;

import java.util.List;
import java.util.Optional;

/**
 * Persistent memory store — key-value interface for storing durable facts.
 * Implementations: FileSystemMemoryStore (Phase 1), VectorMemoryStore (Phase 3).
 */
public interface MemoryStore {
    void save(String key, String value);
    Optional<String> load(String key);
    List<String> search(String query, int limit);
}
