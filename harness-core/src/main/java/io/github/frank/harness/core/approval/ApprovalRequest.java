package io.github.frank.harness.core.approval;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public record ApprovalRequest(
    String id,
    String toolName,
    String command,
    String riskType,
    String description,
    CompletableFuture<Boolean> decision
) {
    public static ApprovalRequest create(String toolName, String command,
                                          ApprovalPattern pattern) {
        return new ApprovalRequest(
            UUID.randomUUID().toString(),
            toolName,
            command,
            pattern.type(),
            pattern.description(),
            new CompletableFuture<>()
        );
    }
}
