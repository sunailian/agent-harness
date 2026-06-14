/**
 * Harness AI LangChain4j 适配器 — 可选 SPI 扩展.
 *
 * <p>通过 {@link java.util.ServiceLoader} 加载，
 * 将 LangChain4j 的 ChatLanguageModel 包装为 Harness 的 {@code ModelProvider} 接口。</p>
 *
 * <p>此模块编译期不依赖 harness-core，仅依赖 harness-ai-core。</p>
 */
package io.github.frank.harness.ai.langchain4j;
