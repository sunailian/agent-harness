/**
 * Harness AI Core — 模型适配层.
 *
 * <h2>职责</h2>
 * 把不同模型供应商的差异统一成 Message、Tool、AssistantEvent 和 stream()。
 * 上层 Agent Loop 不需要知道底层是 OpenAI 还是 Anthropic。
 *
 * <h2>包结构</h2>
 * <ul>
 *   <li>{@code protocol} — Message / Tool / Content / JsonSchema 协议类型</li>
 *   <li>{@code provider} — ModelProvider 接口 + ModelConfig</li>
 *   <li>{@code provider.openai} — OpenAIProvider（OkHttp + SSE）</li>
 *   <li>{@code stream} — AssistantEvent 流式事件体系</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * var provider = new OpenAIProvider("sk-...");
 * provider.stream(messages, tools, config, event -> {
 *     switch (event) {
 *         case TextDelta t -> System.out.print(t.text());
 *         case ToolCallStart s -> System.out.println("Calling: " + s.name());
 *         case Done d -> System.out.println("Done: " + d.stopReason());
 *         default -> {}
 *     }
 * });
 * }</pre>
 */
package io.github.frank.harness.ai;
