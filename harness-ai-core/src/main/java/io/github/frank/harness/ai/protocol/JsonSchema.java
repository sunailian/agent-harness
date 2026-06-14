package io.github.frank.harness.ai.protocol;

import java.util.List;
import java.util.Map;

public record JsonSchema(
    String type,
    Map<String, PropertyDef> properties,
    List<String> required
) {
    public record PropertyDef(String type, String description, Object defaultValue) {}

    public JsonSchema {
        if (type == null) type = "object";
        if (properties == null) properties = Map.of();
        if (required == null) required = List.of();
    }
}
