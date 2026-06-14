package io.github.frank.harness.coding.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ResourceLoader — loads project rules from AGENTS.md / CLAUDE.md / .cursorrules.
 */
public class ResourceLoader {
    private final Path workdir;

    public ResourceLoader(Path workdir) {
        this.workdir = workdir;
    }

    public String loadProjectRules() {
        for (String name : new String[]{"AGENTS.md", "CLAUDE.md", ".cursorrules"}) {
            Path p = workdir.resolve(name);
            if (Files.exists(p)) {
                try {
                    return Files.readString(p);
                } catch (IOException ignored) {}
            }
        }
        return "";
    }

    public String expandSkills(String text) {
        // Placeholder: resolve /skill name → SKILL.md content
        return text;
    }
}
