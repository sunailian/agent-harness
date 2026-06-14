# S4: harness-coding 产品层实现计划

> **目标**: 实现 7 个内置工具、JSONL 会话存储、资源加载、CLI 入口

**执行策略**: 批量创建所有文件后一次性编译验证，然后分批 commit。

---

### 任务 1：7 个内置工具（ToolRegistry + Read/Write/Bash/Grep/Find/Ls/Patch）

文件列表：
```
harness-coding/src/main/java/io/github/frank/harness/coding/tool/
├── ToolRegistry.java
├── ReadTool.java
├── WriteTool.java
├── BashTool.java
├── GrepTool.java
├── FindTool.java
├── LsTool.java
└── PatchTool.java
```

- [ ] 创建全部 8 个文件 → 编译 → commit

### 任务 2：会话管理（AgentSession + JsonlSessionStore + SessionManager）

- [ ] 创建 3 个文件 → 编译 → commit

### 任务 3：资源加载（ResourceLoader + SkillResolver + ProjectContext）

- [ ] 创建 3 个文件 → 编译 → commit

### 任务 4：压缩策略占位（CompactionService + SlidingWindow）

- [ ] 创建 2 个文件 → 编译 → commit

### 任务 5：CLI 入口（HarnessCLI + InteractiveLoop）

- [ ] 创建 2 个文件 → 编译 → commit

### 任务 6：全量验证 → push GitHub
