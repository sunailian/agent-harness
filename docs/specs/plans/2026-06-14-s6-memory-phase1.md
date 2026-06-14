# S6: Memory Phase 1 — 基础记忆存储实现计划

> **目标**: 实现 FileSystemMemoryStore + ExactTokenCounter，跑通 Agent 的记忆读写闭环

---

### 任务 1：FileSystemMemoryStore

文件：`harness-core/src/main/java/io/github/frank/harness/core/memory/FileSystemMemoryStore.java`

- 基于文件的 key-value 存储，每个 key 一个 JSON 文件
- 实现 MemoryStore 接口的 save/load/search
- search 用简单的文本包含匹配（Phase 3 升级为向量检索）

### 任务 2：ExactTokenCounter

文件：`harness-core/src/main/java/io/github/frank/harness/core/memory/ExactTokenCounter.java`

- 实现 ContextWindow 接口的 fit() 方法
- 用字符数/4 估算 token（对齐 SlidingWindowStrategy）
- 从后往前截断，保留 system prompt

### 任务 3：编译 + commit + push
