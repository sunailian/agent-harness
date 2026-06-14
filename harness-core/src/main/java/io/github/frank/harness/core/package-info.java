/**
 * Harness Core — Agent 运行时.
 *
 * <h2>职责</h2>
 * Agent 状态机、双层 AgentLoop、工具执行管线（Prepare → Hook → Execute → Finalize）、
 * Steering/FollowUp 队列。不关心终端 UI 和持久化。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code agent.Agent} — 状态机 + context/tools/config 持有者</li>
 *   <li>{@code loop.AgentLoop} — runAgentLoop() 双层循环</li>
 *   <li>{@code hook.ToolHook} — 工具执行前后拦截</li>
 *   <li>{@code queue.SteeringQueue} — 中途转向消息队列</li>
 *   <li>{@code queue.FollowUpQueue} — 后续任务队列</li>
 *   <li>{@code memory.MemoryStore} — 记忆持久化接口</li>
 * </ul>
 *
 * <h2>为什么 loop 不直接写磁盘</h2>
 * runAgentLoop 只返回事件和新消息，产品层（harness-coding）负责持久化。
 * 这样 loop 是完全可测试的状态转换。
 */
package io.github.frank.harness.core;
