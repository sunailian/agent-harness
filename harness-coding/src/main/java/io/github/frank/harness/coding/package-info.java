/**
 * Harness Coding — Coding Agent 产品层.
 *
 * <h2>职责</h2>
 * 把 Agent 运行时变成每天能用的开发工具：内置工具集、会话 JSONL 持久化、
 * 项目资源加载（AGENTS.md / skills / prompts）、上下文压缩、CLI 入口。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code session.AgentSession} — 会话编排（桥接 Agent + 存储 + UI）</li>
 *   <li>{@code tool.ToolRegistry} — 工具注册中心</li>
 *   <li>{@code resource.ResourceLoader} — 项目规则/技能加载</li>
 *   <li>{@code compaction.CompactionService} — 上下文压缩编排</li>
 *   <li>{@code cli.HarnessCLI} — 命令行入口</li>
 * </ul>
 */
package io.github.frank.harness.coding;
