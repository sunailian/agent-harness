package io.github.frank.harness.core.approval;

import java.util.regex.Pattern;

public record ApprovalPattern(
    String type,
    String description,
    Pattern regex
) {
    public static ApprovalPattern of(String type, String description, String pattern) {
        return new ApprovalPattern(type, description, Pattern.compile(pattern));
    }

    public boolean matches(String command) {
        return regex.matcher(command).find();
    }
}
