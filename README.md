# Agent Harness

Java 21 AI Agent Harness 框架，参考 [Pi Agent](https://github.com/earendil-works/pi) 三层架构设计。

## 模块

| 模块 | 职责 |
|------|------|
| harness-ai-core | 模型适配层（Message/Tool/Event 协议 + OpenAI/Anthropic Provider） |
| harness-ai-langchain4j | LangChain4j 适配器（可选 SPI 扩展） |
| harness-core | Agent 运行时（AgentLoop + hooks + steering/followUp 队列） |
| harness-coding | Coding Agent 产品层（工具 + 会话管理 + CLI） |
| harness-bom | 统一依赖版本管理 |

## 依赖方向

```
harness-coding → harness-core → harness-ai-core
                    ↘ (SPI)
               harness-ai-langchain4j
```

## 构建

```bash
mvn clean install
```

## 运行

```bash
cd /path/to/your/project
java -jar harness-coding/target/harness-coding-0.1.0-SNAPSHOT.jar
```

## 许可

MIT
