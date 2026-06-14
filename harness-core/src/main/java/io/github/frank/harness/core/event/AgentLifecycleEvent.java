package io.github.frank.harness.core.event;

import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.Message;
import io.github.frank.harness.ai.protocol.ToolResult;

public sealed interface AgentLifecycleEvent
        permits AgentLifecycleEvent.LoopStarted,
                AgentLifecycleEvent.TextDelta,
                AgentLifecycleEvent.ToolCallStart,
                AgentLifecycleEvent.ToolCallEnd,
                AgentLifecycleEvent.TurnComplete,
                AgentLifecycleEvent.LoopComplete,
                AgentLifecycleEvent.ErrorEvent {

    record LoopStarted() implements AgentLifecycleEvent {}
    record TextDelta(String text) implements AgentLifecycleEvent {}
    record ToolCallStart(Content.ToolCallContent call) implements AgentLifecycleEvent {}
    record ToolCallEnd(Content.ToolCallContent call, ToolResult result) implements AgentLifecycleEvent {}
    record TurnComplete(Message assistantMessage) implements AgentLifecycleEvent {}
    record LoopComplete(String stopReason) implements AgentLifecycleEvent {}
    record ErrorEvent(String message, Throwable cause) implements AgentLifecycleEvent {}
}
