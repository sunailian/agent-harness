# Agent Harness 项目骨架实现计划

> **面向 AI 代理的工作者：** 使用 subagent-driven-development 或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 搭建 Java 21 五模块三层 Agent Harness 框架的项目骨架，包含父 POM、模块声明、.gitignore、README 和目录结构

**架构：** Maven 多模块项目，依赖方向 harness-coding → harness-core → harness-ai-core（编译期 enforce），harness-ai-langchain4j 为可选 SPI 扩展

**技术栈：** Java 21, Maven 3.9+, OkHttp 4.x, Jackson 2.x, JUnit 5, AssertJ, WireMock

---

## 文件结构

```
agent-harness/
├── pom.xml                          # 父 POM（groupId: io.github.frank）
├── .gitignore                       # Java/Maven/IDE 忽略规则
├── README.md                        # 项目说明
├── harness-bom/
│   └── pom.xml                      # BOM 统一依赖版本
├── harness-ai-core/
│   ├── pom.xml
│   └── src/main/java/io/github/frank/harness/ai/
│       └── package-info.java
├── harness-ai-langchain4j/
│   ├── pom.xml
│   └── src/main/java/io/github/frank/harness/ai/langchain4j/
│       └── package-info.java
├── harness-core/
│   ├── pom.xml
│   └── src/main/java/io/github/frank/harness/core/
│       └── package-info.java
└── harness-coding/
    ├── pom.xml
    └── src/main/java/io/github/frank/harness/coding/
        └── package-info.java
```

---

### 任务 1：创建父 POM + .gitignore + README

**文件：**
- 创建：`pom.xml`
- 创建：`.gitignore`
- 创建：`README.md`

- [ ] **步骤 1：创建父 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.frank</groupId>
    <artifactId>agent-harness</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Agent Harness</name>
    <description>A Java 21 AI Agent Harness framework inspired by Pi Agent, focused on Coding Agent and Memory/Context exploration</description>

    <modules>
        <module>harness-bom</module>
        <module>harness-ai-core</module>
        <module>harness-ai-langchain4j</module>
        <module>harness-core</module>
        <module>harness-coding</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Dependency versions -->
        <okhttp.version>4.12.0</okhttp.version>
        <jackson.version>2.18.2</jackson.version>
        <junit.version>5.11.4</junit.version>
        <assertj.version>3.27.3</assertj.version>
        <wiremock.version>3.10.0</wiremock.version>
        <slf4j.version>2.0.16</slf4j.version>
        <logback.version>1.5.15</logback.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- OkHttp -->
            <dependency>
                <groupId>com.squareup.okhttp3</groupId>
                <artifactId>okhttp</artifactId>
                <version>${okhttp.version}</version>
            </dependency>
            <dependency>
                <groupId>com.squareup.okhttp3</groupId>
                <artifactId>okhttp-sse</artifactId>
                <version>${okhttp.version}</version>
            </dependency>

            <!-- Jackson -->
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>${jackson.version}</version>
            </dependency>

            <!-- Logging -->
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>

            <!-- Test -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.wiremock</groupId>
                <artifactId>wiremock-standalone</artifactId>
                <version>${wiremock.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>21</source>
                        <target>21</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.2</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **步骤 2：验证 POM 格式正确**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn validate
```
预期：BUILD SUCCESS（因模块子目录还不存在，可能报警告，忽略）

- [ ] **步骤 3：创建 .gitignore**

```
# Maven
target/
*.jar
*.war
! .mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
.project
.classpath
.settings/

# OS
.DS_Store
Thumbs.db

# Java
*.class
*.log

# Harness
.harness/sessions/
.harness/config.local.yaml

# Secrets
*.key
.env
```

- [ ] **步骤 4：创建 README.md**

```markdown
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
```

- [ ] **步骤 5：Commit**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness
git add pom.xml .gitignore README.md
git commit -m "chore: init project skeleton — parent POM, .gitignore, README"
```

---

### 任务 2：创建 harness-bom 模块

**文件：**
- 创建：`harness-bom/pom.xml`

- [ ] **步骤 1：创建 harness-bom/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-bom</artifactId>
    <packaging>pom</packaging>

    <name>Harness BOM</name>
    <description>Bill of Materials — unified dependency version management for all harness modules</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.frank</groupId>
                <artifactId>harness-ai-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.frank</groupId>
                <artifactId>harness-ai-langchain4j</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.frank</groupId>
                <artifactId>harness-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.frank</groupId>
                <artifactId>harness-coding</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **步骤 2：验证 BOM 可解析**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn validate -pl harness-bom
```
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-bom/pom.xml
git commit -m "chore: add harness-bom module"
```

---

### 任务 3：创建 harness-ai-core 模块

**文件：**
- 创建：`harness-ai-core/pom.xml`
- 创建：`harness-ai-core/src/main/java/io/github/frank/harness/ai/package-info.java`

- [ ] **步骤 1：创建 harness-ai-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-ai-core</artifactId>

    <name>Harness AI Core</name>
    <description>Model abstraction layer — unified Message/Tool/Event protocol and pure-HTTP provider implementations</description>

    <dependencies>
        <!-- OkHttp for HTTP/SSE -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp-sse</artifactId>
        </dependency>

        <!-- Jackson for JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建 package-info.java**

```java
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
```

- [ ] **步骤 3：验证模块编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -pl harness-ai-core
```
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-ai-core/
git commit -m "chore: add harness-ai-core module"
```

---

### 任务 4：创建 harness-ai-langchain4j 模块

**文件：**
- 创建：`harness-ai-langchain4j/pom.xml`
- 创建：`harness-ai-langchain4j/src/main/java/io/github/frank/harness/ai/langchain4j/package-info.java`

- [ ] **步骤 1：创建 harness-ai-langchain4j/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-ai-langchain4j</artifactId>

    <name>Harness AI LangChain4j</name>
    <description>Optional LangChain4j adapter — SPI-based ModelProvider wrapper for the LangChain4j ecosystem</description>

    <dependencies>
        <dependency>
            <groupId>io.github.frank</groupId>
            <artifactId>harness-ai-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建 package-info.java**

```java
/**
 * Harness AI LangChain4j 适配器 — 可选 SPI 扩展.
 *
 * <p>通过 {@link java.util.ServiceLoader} 加载，
 * 将 LangChain4j 的 ChatLanguageModel 包装为 Harness 的 {@code ModelProvider} 接口。</p>
 *
 * <p>此模块编译期不依赖 harness-core，仅依赖 harness-ai-core。</p>
 */
package io.github.frank.harness.ai.langchain4j;
```

- [ ] **步骤 3：验证编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -pl harness-ai-langchain4j
```
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-ai-langchain4j/
git commit -m "chore: add harness-ai-langchain4j module"
```

---

### 任务 5：创建 harness-core 模块

**文件：**
- 创建：`harness-core/pom.xml`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/package-info.java`

- [ ] **步骤 1：创建 harness-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-core</artifactId>

    <name>Harness Core</name>
    <description>Agent runtime — AgentLoop, hooks, steering/followUp queues, state machine</description>

    <dependencies>
        <!-- Depends on AI abstraction layer -->
        <dependency>
            <groupId>io.github.frank</groupId>
            <artifactId>harness-ai-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Jackson for JSON (used in tool args parsing) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建 package-info.java**

```java
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
```

- [ ] **步骤 3：验证编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -pl harness-core
```
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-core/
git commit -m "chore: add harness-core module"
```

---

### 任务 6：创建 harness-coding 模块

**文件：**
- 创建：`harness-coding/pom.xml`
- 创建：`harness-coding/src/main/java/io/github/frank/harness/coding/package-info.java`

- [ ] **步骤 1：创建 harness-coding/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.frank</groupId>
        <artifactId>agent-harness</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>harness-coding</artifactId>

    <name>Harness Coding</name>
    <description>Coding Agent product layer — built-in tools, session management, resource loading, CLI</description>

    <dependencies>
        <!-- Depends on Agent runtime -->
        <dependency>
            <groupId>io.github.frank</groupId>
            <artifactId>harness-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Note: harness-coding does NOT directly depend on harness-ai-core
             All model interaction goes through harness-core API -->

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Runtime logging implementation -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>${logback.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>io.github.frank.harness.coding.cli.HarnessCLI</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 2：创建 package-info.java**

```java
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
```

- [ ] **步骤 3：验证编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -pl harness-coding
```
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-coding/
git commit -m "chore: add harness-coding module"
```

---

### 任务 7：全量构建验证

- [ ] **步骤 1：全量编译 + 测试（此处测试为空，预期 skip）**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn clean install
```
预期：BUILD SUCCESS — 五个模块全部编译通过

- [ ] **步骤 2：验证模块依赖方向正确**

```bash
# harness-coding 的依赖树不应出现 harness-ai-core（只能通过 harness-core 间接依赖）
mvn dependency:tree -pl harness-coding | grep harness-ai-core
```
预期：输出中包含 `harness-ai-core`（作为传递依赖），但 harness-coding 的 POM 中不应直接声明它。

- [ ] **步骤 3：验证最后目录结构**

```bash
find /Users/sunhanbin/Documents/workspace/agent-harness -not -path '*/target/*' -not -path '*/.git/*' -type f | sort
```

- [ ] **步骤 4：Commit**

```bash
git add -A
git commit -m "chore: finalize project skeleton — all 5 modules verified"
```
