package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.Tool;
import java.util.ArrayList;
import java.util.List;

public class ToolRegistry {
    private final List<Tool> tools = new ArrayList<>();

    public ToolRegistry register(Tool tool) {
        tools.add(tool);
        return this;
    }

    public List<Tool> all() {
        return List.copyOf(tools);
    }
}
