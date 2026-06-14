package io.github.frank.harness.ai.provider;

import java.util.Map;

public record ModelConfig(
    String model,
    Double temperature,
    Integer maxTokens,
    Map<String, Object> extra
) {
    public ModelConfig {
        if (temperature == null) temperature = 0.1;
        if (extra == null) extra = Map.of();
    }

    public static ModelConfig of(String model) {
        return new ModelConfig(model, 0.1, 4096, Map.of());
    }
}
