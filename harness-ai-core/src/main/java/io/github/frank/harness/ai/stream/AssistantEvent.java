package io.github.frank.harness.ai.stream;

import java.util.Map;

public sealed interface AssistantEvent
        permits AssistantEvent.TextDelta, AssistantEvent.ThinkDelta,
                AssistantEvent.ToolCallStart, AssistantEvent.ToolCallArgs,
                AssistantEvent.ToolCallDone, AssistantEvent.Done {

    record TextDelta(int index, String text) implements AssistantEvent {}
    record ThinkDelta(int index, String reasoning) implements AssistantEvent {}
    record ToolCallStart(String id, String name) implements AssistantEvent {}
    record ToolCallArgs(String id, String delta) implements AssistantEvent {}
    record ToolCallDone(String id, String name, String arguments) implements AssistantEvent {}
    record Done(String stopReason, Map<String, Object> usage) implements AssistantEvent {}
}
