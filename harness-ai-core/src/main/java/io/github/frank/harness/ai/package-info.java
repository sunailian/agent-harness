/**
 * Harness AI Core — 模型适配层.
 *
 * <h2>职责</h2>
 * 把不同模型供应商的差异统一成 Message、Tool、AssistantEvent 和 streamSimple()。
 * 上层 Agent Loop 不需要知道底层是 OpenAI 还是 Anthropic。
 *
 * <h2>核心类型</h2>
 * <ul>
 *   <li>{@code protocol.Message} — 统一消息类型（支持多内容块）</li>
 *   <li>{@code protocol.Tool} — 工具定义（JSON Schema 参数 + 执行体）</li>
 *   <li>{@code protocol.Content} — 密封内容块（Text/Think/ToolCall/ToolResult）</li>
 *   <li>{@code stream.AssistantEvent} — 流式事件体系</li>
 *   <li>{@code provider.ModelProvider} — 供应商接口</li>
 * </ul>
 *
 * <h2>实现</h2>
 * <ul>
 *   <li>{@code provider.openai.OpenAIProvider} — OpenAI Chat Completions API</li>
 *   <li>{@code provider.anthropic.AnthropicProvider} — Anthropic Messages API</li>
 * </ul>
 *
 * @see io.github.frank.harness.ai.protocol.Message
 * @see io.github.frank.harness.ai.provider.ModelProvider
 */
package io.github.frank.harness.ai;
