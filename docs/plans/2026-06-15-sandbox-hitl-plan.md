# Sandbox + HITL 联合实现计划

> **面向 AI 代理的工作者：** 此计划包含 14 个任务，按依赖顺序排列。sandbox 和 HITL 各自独立的前置任务可并行。两者在 Agent + CLI 处汇合。

**目标：** 为 agent-harness 添加 Docker 容器级沙箱隔离 + 人类审批（HITL）能力

**架构：** harness-core 新增 sandbox/ 和 approval/ 两个包。AgentLoop 零改动。ToolHook 接口不变。HarnessCLI 改为双线程模型。

**技术栈：** Java 17 + Maven + OkHttp 4.12.0（Unix Domain Socket） + java.util.concurrent

**依赖关系：**
```
Task 1 (Sandbox 接口)
├── Task 2 (LocalSandbox) ──────┐
├── Task 3 (DockerClient) ──────┤
│   └── Task 4 (DockerSandbox) ─┤
│       └── Task 5 (pom OkHttp) ┤
│                               ├── Task 8 (Agent 改造) ── Task 9 (CLI 改造)
├── Task 6 (Approval 数据结构) ─┤
│   └── Task 7 (ApprovalHook) ──┘
│
├── Task 10 (工具改造) ── 依赖 Agent 有 sandbox
├── Task 11-13 (测试)
└── Task 14 (编译验证)
```

**文件结构：**

| 文件 | 操作 | 职责 |
|------|------|------|
| `harness-core/.../sandbox/Sandbox.java` | 创建 | 沙箱接口（6 methods + Closeable） |
| `harness-core/.../sandbox/SandboxConfig.java` | 创建 | 配置 record（mode/image/timeout/maxBytes） |
| `harness-core/.../sandbox/SandboxProvider.java` | 创建 | 工厂接口（acquire/release） |
| `harness-core/.../sandbox/LocalSandbox.java` | 创建 | ProcessBuilder 实现 + 锁 |
| `harness-core/.../sandbox/LocalSandboxProvider.java` | 创建 | 单例 provider |
| `harness-core/.../sandbox/DockerClient.java` | 创建 | OkHttp + Unix Domain Socket HTTP 客户端 |
| `harness-core/.../sandbox/DockerSandbox.java` | 创建 | 容器生命周期 + exec |
| `harness-core/.../sandbox/DockerSandboxProvider.java` | 创建 | 每 session 创建/销毁容器 |
| `harness-core/.../approval/ApprovalPattern.java` | 创建 | 危险命令正则模式 |
| `harness-core/.../approval/ApprovalRequest.java` | 创建 | 审批请求（含 CompletableFuture） |
| `harness-core/.../approval/ApprovalHook.java` | 创建 | ToolHook 实现：阻塞等人决策 |
| `harness-core/.../agent/Agent.java` | 修改 | 新增 sandbox 字段 + getter |
| `harness-core/pom.xml` | 修改 | 新增 OkHttp 依赖 |
| `harness-coding/.../tool/BashTool.java` | 修改 | 改用 sandbox.execute() |
| `harness-coding/.../tool/ReadTool.java` | 修改 | 改用 sandbox.readFile() |
| `harness-coding/.../tool/WriteTool.java` | 修改 | 改用 sandbox.writeFile() |
| `harness-coding/.../cli/HarnessCLI.java` | 修改 | 双线程 + sandbox 注入 + 审批轮询 |

---

### 任务 1：Sandbox 接口层

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/Sandbox.java`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/SandboxConfig.java`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/SandboxProvider.java`

- [ ] **步骤 1：创建 Sandbox.java**

```java
package io.github.frank.harness.core.sandbox;

import io.github.frank.harness.ai.protocol.ToolResult;
import java.time.Duration;
import java.util.List;

public interface Sandbox extends AutoCloseable {
    ToolResult execute(String command, Duration timeout);
    String readFile(String path);
    void writeFile(String path, String content);
    List<String> listDir(String path);
    List<String> glob(String pattern);
    @Override
    void close();
}
```

- [ ] **步骤 2：创建 SandboxConfig.java**

```java
package io.github.frank.harness.core.sandbox;

import java.nio.file.Path;
import java.time.Duration;

public record SandboxConfig(
    SandboxMode mode,
    Path hostWorkdir,
    String dockerImage,
    Duration defaultTimeout,
    long maxOutputBytes
) {
    public enum SandboxMode { LOCAL, DOCKER }

    public static SandboxConfig local(Path workdir) {
        return new SandboxConfig(SandboxMode.LOCAL, workdir, null,
            Duration.ofSeconds(120), 1_048_576);
    }

    public static SandboxConfig docker(Path workdir, String image) {
        return new SandboxConfig(SandboxMode.DOCKER, workdir, image,
            Duration.ofSeconds(120), 1_048_576);
    }
}
```

- [ ] **步骤 3：创建 SandboxProvider.java**

```java
package io.github.frank.harness.core.sandbox;

public interface SandboxProvider {
    Sandbox acquire(SandboxConfig config);
    void release(Sandbox sandbox);
}
```

- [ ] **步骤 4：验证**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/sandbox/
git commit -m "feat: add sandbox interface layer (Sandbox, SandboxConfig, SandboxProvider)"
```

---

### 任务 2：LocalSandbox 实现

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/LocalSandbox.java`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/LocalSandboxProvider.java`

- [ ] **步骤 1：创建 LocalSandbox.java**

```java
package io.github.frank.harness.core.sandbox;

import io.github.frank.harness.ai.protocol.ToolResult;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class LocalSandbox implements Sandbox {
    private final Path workdir;
    private final Duration defaultTimeout;
    private final long maxOutputBytes;
    private final Object execLock = new Object();

    public LocalSandbox(SandboxConfig config) {
        this.workdir = config.hostWorkdir();
        this.defaultTimeout = config.defaultTimeout();
        this.maxOutputBytes = config.maxOutputBytes();
    }

    @Override
    public ToolResult execute(String command, Duration timeout) {
        synchronized (execLock) {
            try {
                var pb = new ProcessBuilder("sh", "-c", command);
                pb.directory(workdir.toFile());
                pb.redirectErrorStream(true);
                var p = pb.start();
                var to = timeout != null ? timeout : defaultTimeout;
                boolean finished = p.waitFor(to.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    return ToolResult.error("Timed out after " + to.toSeconds() + "s");
                }
                byte[] bytes = p.getInputStream().readAllBytes();
                if (bytes.length > maxOutputBytes) {
                    return ToolResult.error("Output exceeds " + maxOutputBytes + " bytes");
                }
                String out = new String(bytes);
                return ToolResult.success(out.isEmpty() ? "(no output)" : out);
            } catch (IOException e) {
                return ToolResult.error("Execution failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Interrupted");
            }
        }
    }

    @Override
    public String readFile(String path) {
        try {
            Path resolved = workdir.resolve(sanitize(path));
            return Files.readString(resolved);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void writeFile(String path, String content) {
        try {
            Path resolved = workdir.resolve(sanitize(path));
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
        } catch (IOException e) {
            throw new RuntimeException("Write failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listDir(String path) {
        try (Stream<Path> s = Files.list(workdir.resolve(sanitize(path)))) {
            return s.map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public List<String> glob(String pattern) {
        try {
            var matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + sanitize(pattern));
            try (Stream<Path> s = Files.walk(workdir)) {
                return s.filter(p -> matcher.matches(workdir.relativize(p)))
                    .map(p -> workdir.relativize(p).toString()).toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void close() {
        // no-op for local sandbox
    }

    /** Prevent path traversal. Strip leading / and .. segments. */
    private String sanitize(String path) {
        return path.replaceAll("^\\.\\./|^/", "").replaceAll("/\\.\\./", "/");
    }
}
```

- [ ] **步骤 2：创建 LocalSandboxProvider.java**

```java
package io.github.frank.harness.core.sandbox;

public final class LocalSandboxProvider implements SandboxProvider {
    private volatile LocalSandbox instance;

    @Override
    public Sandbox acquire(SandboxConfig config) {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    instance = new LocalSandbox(config);
                }
            }
        }
        return instance;
    }

    @Override
    public void release(Sandbox sandbox) {
        // singleton, no release needed
    }
}
```

- [ ] **步骤 3：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/sandbox/LocalSandbox.java \
        harness-core/src/main/java/io/github/frank/harness/core/sandbox/LocalSandboxProvider.java
git commit -m "feat: LocalSandbox implementation with process isolation lock"
```

---

### 任务 3：DockerClient（OkHttp + Unix Socket）

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerClient.java`

- [ ] **步骤 1：创建 DockerClient.java**

```java
package io.github.frank.harness.core.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for Docker daemon via Unix domain socket.
 * Uses Java 16+ UnixDomainSocketAddress — zero new dependencies.
 *
 * Implemented endpoints:
 *   POST /containers/create   → containerId
 *   POST /containers/{id}/start
 *   POST /containers/{id}/exec → execId
 *   POST /exec/{id}/start      → stdout
 *   DELETE /containers/{id}?force=true
 */
class DockerClient implements AutoCloseable {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http;
    private final String baseUrl;

    DockerClient(String socketPath) {
        this.baseUrl = "http://localhost";
        this.http = new OkHttpClient.Builder()
            .socketFactory(new UnixSocketFactory(socketPath))
            .build();
    }

    /** Create container from image, return container ID. */
    String createContainer(String image, String hostWorkdir, String containerWorkdir) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("Image", image);
            body.putArray("Cmd").add("tail").add("-f").add("/dev/null");
            body.put("WorkingDir", containerWorkdir);

            ObjectNode hostConfig = body.putObject("HostConfig");
            hostConfig.putArray("Binds").add(hostWorkdir + ":" + containerWorkdir);

            var request = post("/containers/create", body.toString());
            try (var resp = http.newCall(request).execute()) {
                JsonNode json = mapper.readTree(resp.body().string());
                if (resp.code() >= 400) {
                    throw new RuntimeException("Create container failed: " + resp.code() + " " + json);
                }
                return json.get("Id").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Docker create container failed", e);
        }
    }

    /** Start an existing container. */
    void startContainer(String containerId) {
        try {
            var request = post("/containers/" + containerId + "/start", "");
            try (var resp = http.newCall(request).execute()) {
                if (resp.code() >= 400) {
                    throw new RuntimeException("Start container failed: " + resp.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Docker start container failed", e);
        }
    }

    /** Create exec instance in container. Returns exec ID. */
    String createExec(String containerId, String command) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putArray("Cmd").add("sh").add("-c").add(command);
            body.put("AttachStdout", true);
            body.put("AttachStderr", true);

            var request = post("/containers/" + containerId + "/exec", body.toString());
            try (var resp = http.newCall(request).execute()) {
                JsonNode json = mapper.readTree(resp.body().string());
                if (resp.code() >= 400) {
                    throw new RuntimeException("Create exec failed: " + resp.code());
                }
                return json.get("Id").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Docker create exec failed", e);
        }
    }

    /** Start exec and return stdout. */
    String startExec(String execId, Duration timeout) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("Detach", false);
            body.put("Tty", false);

            var request = post("/exec/" + execId + "/start", body.toString());
            // Exec start can take time — use a longer timeout
            var clientWithTimeout = http.newBuilder()
                .callTimeout(java.time.Duration.ofSeconds(
                    timeout != null ? timeout.toSeconds() + 5 : 125))
                .build();
            try (var resp = clientWithTimeout.newCall(request).execute()) {
                if (resp.code() >= 400) {
                    throw new RuntimeException("Start exec failed: " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "";
            }
        } catch (IOException e) {
            throw new RuntimeException("Docker start exec failed", e);
        }
    }

    /** Delete (force remove) a container. */
    void deleteContainer(String containerId) {
        try {
            var request = new Request.Builder()
                .url(baseUrl + "/containers/" + containerId + "?force=true")
                .delete()
                .build();
            try (var resp = http.newCall(request).execute()) {
                // 204 or 200 = success, 404 = already gone (ok)
                if (resp.code() >= 400 && resp.code() != 404) {
                    // silently ignore — best-effort cleanup
                }
            }
        } catch (IOException e) {
            // silently ignore — best-effort cleanup
        }
    }

    @Override
    public void close() {
        // OkHttpClient manages its own connection pool
    }

    // ── private helpers ────────────────────────────────────

    private Request post(String path, String jsonBody) {
        return new Request.Builder()
            .url(baseUrl + path)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();
    }

    /**
     * SocketFactory for Unix domain sockets via Java 16+ UnixDomainSocketAddress.
     */
    private static class UnixSocketFactory extends javax.net.SocketFactory {
        private final Path socketPath;

        UnixSocketFactory(String path) {
            this.socketPath = Path.of(path);
        }

        @Override
        public Socket createSocket() throws IOException {
            var address = UnixDomainSocketAddress.of(socketPath);
            var channel = SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            channel.connect(address);
            return channel.socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return createSocket(); // ignore host/port for Unix sockets
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            throw new UnsupportedOperationException("Unix domain sockets don't use InetAddress");
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            throw new UnsupportedOperationException("Unix domain sockets don't use InetAddress");
        }

        @Override
        public Socket createSocket(InetAddress address, int port,
                                    InetAddress localAddress, int localPort) {
            throw new UnsupportedOperationException("Unix domain sockets don't use InetAddress");
        }
    }
}
```

- [ ] **步骤 2：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS（编译通过，不要求 Docker 运行）

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerClient.java
git commit -m "feat: DockerClient — OkHttp + Unix Domain Socket REST client for Docker daemon"
```

---

### 任务 4：DockerSandbox + DockerSandboxProvider

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerSandbox.java`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerSandboxProvider.java`

- [ ] **步骤 1：创建 DockerSandbox.java**

```java
package io.github.frank.harness.core.sandbox;

import io.github.frank.harness.ai.protocol.ToolResult;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

public final class DockerSandbox implements Sandbox {
    private final DockerClient client;
    private final String containerId;
    private final String containerWorkdir;
    private final Path hostWorkdir;
    private final Duration defaultTimeout;
    private final long maxOutputBytes;
    private final Object execLock = new Object();

    public DockerSandbox(DockerClient client, SandboxConfig config) {
        this.client = client;
        this.containerWorkdir = "/workspace";
        this.hostWorkdir = config.hostWorkdir();
        this.defaultTimeout = config.defaultTimeout();
        this.maxOutputBytes = config.maxOutputBytes();
        this.containerId = client.createContainer(
            config.dockerImage(), hostWorkdir.toString(), containerWorkdir);
        client.startContainer(containerId);
    }

    @Override
    public ToolResult execute(String command, Duration timeout) {
        synchronized (execLock) {
            try {
                String fullCmd = "cd " + containerWorkdir + " && " + command;
                var to = timeout != null ? timeout : defaultTimeout;
                String execId = client.createExec(containerId, fullCmd);
                String out = client.startExec(execId, to);
                if (out.length() > maxOutputBytes) {
                    return ToolResult.error("Output exceeds " + maxOutputBytes + " bytes");
                }
                return ToolResult.success(out.isEmpty() ? "(no output)" : out);
            } catch (RuntimeException e) {
                return ToolResult.error("Sandbox exec failed: " + e.getMessage());
            }
        }
    }

    @Override
    public String readFile(String path) {
        // Read via exec: cat {path}
        var result = execute("cat " + sanitize(path), Duration.ofSeconds(10));
        return result.success() ? result.output() : null;
    }

    @Override
    public void writeFile(String path, String content) {
        // Write via exec: mkdir -p + heredoc
        String safe = sanitize(path);
        String escaped = content.replace("'", "'\\''");
        execute("mkdir -p $(dirname " + safe + ") && printf '%s' '" + escaped + "' > " + safe,
            Duration.ofSeconds(10));
    }

    @Override
    public List<String> listDir(String path) {
        var result = execute("ls -1 " + sanitize(path), Duration.ofSeconds(10));
        return result.success()
            ? List.of(result.output().trim().split("\\R"))
            : List.of();
    }

    @Override
    public List<String> glob(String pattern) {
        var result = execute("find . -path './" + sanitize(pattern) + "'",
            Duration.ofSeconds(15));
        return result.success()
            ? List.of(result.output().trim().split("\\R"))
            : List.of();
    }

    @Override
    public void close() {
        client.deleteContainer(containerId);
        // TODO: PRODUCTION — 优雅关闭: SIGTERM → wait 10s → SIGKILL
        // TODO: PRODUCTION — 异常时记录容器日志再销毁
    }

    private String sanitize(String path) {
        return path.replaceAll("^\\.\\./|^/", "").replaceAll("/\\.\\./", "/");
    }
}
```

- [ ] **步骤 2：创建 DockerSandboxProvider.java**

```java
package io.github.frank.harness.core.sandbox;

public final class DockerSandboxProvider implements SandboxProvider {
    private final String socketPath;

    public DockerSandboxProvider(String socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public Sandbox acquire(SandboxConfig config) {
        var client = new DockerClient(socketPath);
        return new DockerSandbox(client, config);
    }

    @Override
    public void release(Sandbox sandbox) {
        sandbox.close();
    }
    // TODO: PRODUCTION → PooledDockerSandboxProvider（容器池）
}
```

- [ ] **步骤 3：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerSandbox.java \
        harness-core/src/main/java/io/github/frank/harness/core/sandbox/DockerSandboxProvider.java
git commit -m "feat: DockerSandbox — container lifecycle with bind mount isolation"
```

---

### 任务 5：harness-core pom 添加 OkHttp 依赖

**文件：**
- 修改：`harness-core/pom.xml`

- [ ] **步骤 1：在 dependencies 段添加 OkHttp**

在 `<dependency>` 列表末尾（`</dependencies>` 之前）添加：

```xml
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
```

注：jackson-databind 已通过 `harness-ai-core` 传递依赖可用，显式声明确保 `DockerClient` 可 compile。

- [ ] **步骤 2：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-core/pom.xml
git commit -m "build: add OkHttp + Jackson to harness-core (for DockerClient Unix socket)"
```

---

### 任务 6：ApprovalPattern + ApprovalRequest

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/approval/ApprovalPattern.java`
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/approval/ApprovalRequest.java`

- [ ] **步骤 1：创建 ApprovalPattern.java**

```java
package io.github.frank.harness.core.approval;

import java.util.regex.Pattern;

public record ApprovalPattern(
    String type,
    String description,
    Pattern regex
) {
    public static ApprovalPattern of(String type, String description, String pattern) {
        return new ApprovalPattern(type, description, Pattern.compile(pattern));
    }

    public boolean matches(String command) {
        return regex.matcher(command).find();
    }
}
```

- [ ] **步骤 2：创建 ApprovalRequest.java**

```java
package io.github.frank.harness.core.approval;

import java.util.concurrent.CompletableFuture;

public record ApprovalRequest(
    String id,
    String toolName,
    String command,
    String riskType,
    String description,
    CompletableFuture<Boolean> decision
) {
    public static ApprovalRequest create(String toolName, String command,
                                          ApprovalPattern pattern) {
        return new ApprovalRequest(
            java.util.UUID.randomUUID().toString(),
            toolName,
            command,
            pattern.type(),
            pattern.description(),
            new CompletableFuture<>()
        );
    }
}
```

- [ ] **步骤 3：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/approval/
git commit -m "feat: ApprovalPattern and ApprovalRequest — config-driven dangerous command detection"
```

---

### 任务 7：ApprovalHook

**文件：**
- 创建：`harness-core/src/main/java/io/github/frank/harness/core/approval/ApprovalHook.java`

- [ ] **步骤 1：创建 ApprovalHook.java**

```java
package io.github.frank.harness.core.approval;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frank.harness.ai.protocol.Content;
import io.github.frank.harness.ai.protocol.ToolResult;
import io.github.frank.harness.core.hook.ToolHook;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class ApprovalHook implements ToolHook {
    private final List<ApprovalPattern> patterns;
    private final ConcurrentLinkedQueue<ApprovalRequest> pending;
    private final Duration approvalTimeout;

    public ApprovalHook(List<ApprovalPattern> patterns, Duration approvalTimeout) {
        this.patterns = List.copyOf(patterns);
        this.pending = new ConcurrentLinkedQueue<>();
        this.approvalTimeout = approvalTimeout;
    }

    @Override
    public boolean beforeToolCall(Content.ToolCallContent call, JsonNode args) {
        if (patterns.isEmpty()) return true;

        String command = extractCommand(call, args);
        if (command == null || command.isBlank()) return true;

        for (var p : patterns) {
            if (p.matches(command)) {
                var req = ApprovalRequest.create(
                    call.name(), sanitize(command), p);
                pending.add(req);

                try {
                    return req.decision().get(
                        approvalTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (ExecutionException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ToolResult afterToolCall(Content.ToolCallContent call, ToolResult result) {
        return result;
    }

    // ── HarnessCLI 轮询 API ────────────────────────────────

    public ApprovalRequest pollPending() {
        return pending.poll();
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    // ── private helpers ─────────────────────────────────────

    private String extractCommand(Content.ToolCallContent call, JsonNode args) {
        return args.has("command") ? args.get("command").asText() : null;
    }

    private String sanitize(String command) {
        return command
            .replaceAll("(?:--api-key|--token|--password)\\s+\\S+", "$1 ***");
    }
}
```

- [ ] **步骤 2：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/approval/ApprovalHook.java
git commit -m "feat: ApprovalHook — blocks dangerous commands until human approves"
```

---

### 任务 8：Agent 添加 sandbox 支持

**文件：**
- 修改：`harness-core/src/main/java/io/github/frank/harness/core/agent/Agent.java`

- [ ] **步骤 1：修改 Agent.java**

在 `private final HookChain hooks;` 之后添加：

```java
    private final Sandbox sandbox;
```

在构造函数签名中添加 `Sandbox sandbox` 参数：

```java
    public Agent(List<Tool> tools, ModelConfig config, List<ToolHook> hookList, Sandbox sandbox) {
        this.tools = List.copyOf(tools);
        this.config = config;
        this.hooks = new HookChain(hookList != null ? hookList : List.of());
        this.sandbox = sandbox;
    }
```

添加 getter（在 `getHooks()` 之后）：

```java
    public Sandbox getSandbox() { return sandbox; }
```

在 Builder 中添加 field + method（构造器之前）：

```java
        private Sandbox sandbox;

        public Builder sandbox(Sandbox s) { this.sandbox = s; return this; }
```

修改 Builder.build() 的 new Agent 调用：

```java
        public Agent build() {
            return new Agent(tools, config, hooks, sandbox);
        }
```

注：需要 import `io.github.frank.harness.core.sandbox.Sandbox`。

- [ ] **步骤 2：验证**

```bash
mvn compile -pl harness-core -q
```

预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/main/java/io/github/frank/harness/core/agent/Agent.java
git commit -m "feat: Agent accepts Sandbox — one per session, tool-accessible via getSandbox()"
```

---

### 任务 9：BashTool / ReadTool / WriteTool 改造

**文件：**
- 修改：`harness-coding/src/main/java/io/github/frank/harness/coding/tool/BashTool.java`
- 修改：`harness-coding/src/main/java/io/github/frank/harness/coding/tool/ReadTool.java`
- 修改：`harness-coding/src/main/java/io/github/frank/harness/coding/tool/WriteTool.java`

- [ ] **步骤 1：改造 BashTool.java**

```java
package io.github.frank.harness.coding.tool;

import io.github.frank.harness.ai.protocol.JsonSchema;
import io.github.frank.harness.ai.protocol.Tool;
import io.github.frank.harness.core.sandbox.Sandbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class BashTool {
    private BashTool() {}

    public static Tool create(Sandbox sandbox, Duration timeout) {
        return new Tool("bash", "Execute a shell command in the sandbox.",
            new JsonSchema("object", Map.of(
                "command", new JsonSchema.PropertyDef("string",
                    "Shell command to execute (use '&&' to chain)", null)
            ), List.of("command")),
            args -> sandbox.execute(args.get("command").asText(), timeout));
    }
}
```

- [ ] **步骤 2：改造 ReadTool.java**

```java
public static Tool create(Sandbox sandbox) {
    return new Tool("read", "Read a file from the sandbox.",
        new JsonSchema("object", Map.of(
            "path", new JsonSchema.PropertyDef("string", "File path to read", null)
        ), List.of("path")),
        args -> {
            String content = sandbox.readFile(args.get("path").asText());
            if (content == null) {
                return ToolResult.error("File not found");
            }
            return ToolResult.success(content);
        });
}
```

- [ ] **步骤 3：改造 WriteTool.java**

```java
public static Tool create(Sandbox sandbox) {
    return new Tool("write", "Write content to a file in the sandbox. Creates parent directories.",
        new JsonSchema("object", Map.of(
            "path", new JsonSchema.PropertyDef("string", "File path to write", null),
            "content", new JsonSchema.PropertyDef("string", "Content to write", null)
        ), List.of("path", "content")),
        args -> {
            try {
                sandbox.writeFile(
                    args.get("path").asText(),
                    args.get("content").asText());
                return ToolResult.success("Written");
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
        });
}
```

- [ ] **步骤 4：验证**

```bash
mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add harness-coding/src/main/java/io/github/frank/harness/coding/tool/
git commit -m "refactor: BashTool/ReadTool/WriteTool use Sandbox instead of direct ProcessBuilder/FS"
```

---

### 任务 10：HarnessCLI 改造 — 双线程 + sandbox + 审批

**文件：**
- 修改：`harness-coding/src/main/java/io/github/frank/harness/coding/cli/HarnessCLI.java`

- [ ] **步骤 1：全部重写 HarnessCLI.main()**

```java
package io.github.frank.harness.coding.cli;

import io.github.frank.harness.ai.provider.ModelConfig;
import io.github.frank.harness.ai.provider.ModelProvider;
import io.github.frank.harness.ai.provider.openai.OpenAIProvider;
import io.github.frank.harness.core.agent.Agent;
import io.github.frank.harness.core.approval.ApprovalHook;
import io.github.frank.harness.core.approval.ApprovalPattern;
import io.github.frank.harness.core.event.AgentLifecycleEvent;
import io.github.frank.harness.core.loop.AgentLoop;
import io.github.frank.harness.core.sandbox.*;
import io.github.frank.harness.coding.compaction.SlidingWindowStrategy;
import io.github.frank.harness.coding.resource.ResourceLoader;
import io.github.frank.harness.coding.session.AgentSession;
import io.github.frank.harness.coding.session.JsonlSessionStore;
import io.github.frank.harness.coding.tool.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.*;

public class HarnessCLI {

    public static void main(String[] args) throws IOException {
        var workdir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }

        // ── Sandbox setup ──────────────────────────────────
        String sandboxMode = System.getenv().getOrDefault("HARNESS_SANDBOX", "local");
        SandboxConfig sandboxConfig;
        SandboxProvider provider;
        if ("docker".equalsIgnoreCase(sandboxMode)) {
            sandboxConfig = SandboxConfig.docker(workdir, "ubuntu:22.04");
            provider = new DockerSandboxProvider("/var/run/docker.sock");
        } else {
            sandboxConfig = SandboxConfig.local(workdir);
            provider = new LocalSandboxProvider();
        }
        Sandbox sandbox = provider.acquire(sandboxConfig);

        // ── Approval setup ─────────────────────────────────
        List<ApprovalPattern> patterns = new ArrayList<>();
        // Default patterns (in production, load from config.yaml)
        patterns.add(ApprovalPattern.of("file_destruction", "删除文件/目录",
            "rm\\s+(-rf|--no-preserve-root)"));
        patterns.add(ApprovalPattern.of("privilege_escalation", "提权操作",
            "sudo\\s+|chmod\\s+777"));
        patterns.add(ApprovalPattern.of("git_force", "强制推送",
            "git\\s+push\\s+.*--force"));
        var approvalHook = new ApprovalHook(patterns, Duration.ofSeconds(30));

        // ── Tools组装 ─────────────────────────────────────
        ModelProvider modelProvider = new OpenAIProvider(apiKey);
        var tools = List.of(
            BashTool.create(sandbox, Duration.ofMinutes(2)),
            ReadTool.create(sandbox),
            WriteTool.create(sandbox),
            GrepTool.create(workdir),
            FindTool.create(workdir),
            LsTool.create(workdir),
            PatchTool.create(workdir)
        );

        var agent = Agent.builder()
            .tools(tools)
            .config(ModelConfig.of("gpt-4o"))
            .hooks(List.of(approvalHook))
            .sandbox(sandbox)
            .build();

        var resources = new ResourceLoader(workdir);
        var rules = resources.loadProjectRules();
        if (!rules.isEmpty()) {
            agent.setSystemPrompt("You are a coding agent. " + rules +
                "\nWork in: " + workdir);
        }

        var loop = new AgentLoop(modelProvider);
        var store = new JsonlSessionStore(
            workdir.resolve(".harness/sessions"), UUID.randomUUID().toString());
        var compaction = new SlidingWindowStrategy();
        var session = new AgentSession(UUID.randomUUID().toString(),
            agent, loop, store, compaction);

        // ── Shutdown hook ──────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            provider.release(sandbox);
        }));

        // ── Dual-thread REPL ───────────────────────────────
        var loopExecutor = Executors.newSingleThreadExecutor();
        var scanner = new Scanner(System.in);
        System.out.println("🤖 Harness Coding Agent ready. Workdir: " + workdir);
        System.out.println("   Sandbox: " + sandboxMode);
        System.out.println("   Approval: " + (!patterns.isEmpty() ? patterns.size() + " patterns" : "disabled"));
        System.out.println("Type /exit to quit, /steer <text> to redirect.\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if ("/exit".equals(input)) break;
            if (input.startsWith("/steer ")) {
                session.steer(input.substring(7));
                continue;
            }

            System.out.println();
            Future<?> loopFuture = loopExecutor.submit(() -> {
                session.prompt(input, event -> {
                    switch (event) {
                        case AgentLifecycleEvent.TextDelta td ->
                            System.out.print(td.text());
                        case AgentLifecycleEvent.ToolCallStart ts ->
                            System.out.print("\n🔧 " + ts.call().name() + "... ");
                        case AgentLifecycleEvent.ToolCallEnd te ->
                            System.out.print((te.result().success() ? "✓" : "✗"));
                        case AgentLifecycleEvent.TurnComplete tc ->
                            System.out.println("\n--- turn complete ---");
                        case AgentLifecycleEvent.ErrorEvent ee ->
                            System.err.println("\n❌ " + ee.message());
                        default -> {}
                    }
                });
            });

            // Main thread: poll approvals while loop runs
            while (!loopFuture.isDone()) {
                var approval = approvalHook.pollPending();
                if (approval != null) {
                    System.out.println();
                    System.out.println("⚠️  危险操作 [" + approval.toolName() +
                        "] — 需要审批");
                    System.out.println("   命令: " + approval.command());
                    System.out.println("   风险: " + approval.description());
                    System.out.print("   允许执行? (yes/no): ");
                    String answer = scanner.nextLine().trim();
                    approval.decision().complete("yes".equalsIgnoreCase(answer));
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println();
        }

        loopExecutor.shutdownNow();
        provider.release(sandbox);
        System.out.println("👋 Goodbye.");
    }
}
```

- [ ] **步骤 2：验证**

```bash
mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add harness-coding/src/main/java/io/github/frank/harness/coding/cli/HarnessCLI.java
git commit -m "feat: HarnessCLI dual-thread model — sandbox injection + approval polling"
```

---

### 任务 11：LocalSandbox 测试

**文件：**
- 创建：`harness-core/src/test/java/io/github/frank/harness/core/sandbox/LocalSandboxTest.java`

- [ ] **步骤 1：创建测试文件**

```java
package io.github.frank.harness.core.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSandboxTest {
    @TempDir
    Path tempDir;

    private LocalSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new LocalSandbox(SandboxConfig.local(tempDir));
    }

    @Test
    void executeSimpleCommand() {
        var result = sandbox.execute("echo hello", Duration.ofSeconds(5));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("hello");
    }

    @Test
    void executeTimeout() {
        var result = sandbox.execute("sleep 5", Duration.ofSeconds(1));
        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("Timed out");
    }

    @Test
    void writeAndReadRoundtrip() {
        sandbox.writeFile("test.txt", "hello world");
        String content = sandbox.readFile("test.txt");
        assertThat(content).isEqualTo("hello world");
    }

    @Test
    void readNonExistentFile() {
        String content = sandbox.readFile("nope.txt");
        assertThat(content).isNull();
    }

    @Test
    void listDir() {
        sandbox.writeFile("a.txt", "");
        sandbox.writeFile("b.txt", "");
        var files = sandbox.listDir(".");
        assertThat(files).contains("a.txt", "b.txt");
    }

    @Test
    void pathTraversalBlocked() {
        sandbox.writeFile("safe.txt", "safe");
        // Try to escape sandbox workdir
        String content = sandbox.readFile("../outside.txt");
        assertThat(content).isNull();
    }

    @Test
    void closeIsNoop() {
        sandbox.close(); // should not throw
    }
}
```

- [ ] **步骤 2：运行测试**

```bash
mvn test -pl harness-core -Dtest=LocalSandboxTest -q
```

预期：Tests run: 7, Failures: 0

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/test/
git commit -m "test: LocalSandbox tests — execute, read/write, timeout, path traversal"
```

---

### 任务 12：ApprovalHook 测试

**文件：**
- 创建：`harness-core/src/test/java/io/github/frank/harness/core/approval/ApprovalHookTest.java`

- [ ] **步骤 1：创建测试文件**

```java
package io.github.frank.harness.core.approval;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.frank.harness.ai.protocol.Content;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalHookTest {
    private ApprovalHook hook;
    private JsonNodeFactory jnf;

    @BeforeEach
    void setUp() {
        jnf = JsonNodeFactory.instance;
        var patterns = List.of(
            ApprovalPattern.of("file_destruction", "Delete files",
                "rm\\s+(-rf|--no-preserve-root)"),
            ApprovalPattern.of("priv", "Privilege escalation",
                "sudo\\s+")
        );
        hook = new ApprovalHook(patterns, Duration.ofSeconds(2));
    }

    @Test
    void safeCommandPasses() {
        var call = new Content.ToolCallContent("c1", "bash", "{}");
        var args = jnf.objectNode().put("command", "echo hello");
        assertThat(hook.beforeToolCall(call, args)).isTrue();
        assertThat(hook.hasPending()).isFalse();
    }

    @Test
    void dangerousCommandCreatesPendingRequest() throws Exception {
        var call = new Content.ToolCallContent("c2", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm -rf /tmp/foo");

        // Run beforeToolCall in separate thread (it will block on future.get)
        var future = new CompletableFuture<Boolean>();
        new Thread(() -> future.complete(hook.beforeToolCall(call, args))).start();

        // Wait for request to appear
        Thread.sleep(100);
        assertThat(hook.hasPending()).isTrue();

        // Approve
        var req = hook.pollPending();
        assertThat(req).isNotNull();
        assertThat(req.riskType()).isEqualTo("file_destruction");
        req.decision().complete(true);

        assertThat(future.get()).isTrue();
    }

    @Test
    void userRejectsBlocksExecution() throws Exception {
        var call = new Content.ToolCallContent("c3", "bash", "{}");
        var args = jnf.objectNode().put("command", "sudo rm -rf /");

        var future = new CompletableFuture<Boolean>();
        new Thread(() -> future.complete(hook.beforeToolCall(call, args))).start();

        Thread.sleep(100);
        var req = hook.pollPending();
        req.decision().complete(false);

        assertThat(future.get()).isFalse();
    }

    @Test
    void timeoutAutoRejects() throws Exception {
        var quickHook = new ApprovalHook(
            List.of(ApprovalPattern.of("test", "test", "rm.*")),
            Duration.ofMillis(200));

        var call = new Content.ToolCallContent("c4", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm file.txt");

        // Don't poll/approve — let it timeout
        boolean result = quickHook.beforeToolCall(call, args);
        assertThat(result).isFalse(); // timed out → rejected
    }

    @Test
    void emptyPatternsPassAlways() {
        var noopHook = new ApprovalHook(List.of(), Duration.ofSeconds(1));
        var call = new Content.ToolCallContent("c5", "bash", "{}");
        var args = jnf.objectNode().put("command", "rm -rf /");
        assertThat(noopHook.beforeToolCall(call, args)).isTrue();
    }

    @Test
    void nullCommandPasses() {
        var call = new Content.ToolCallContent("c6", "bash", "{}");
        var args = jnf.objectNode(); // no "command" field
        assertThat(hook.beforeToolCall(call, args)).isTrue();
    }

    @Test
    void afterToolCallIsPassthrough() {
        var call = new Content.ToolCallContent("c7", "bash", "{}");
        var result = io.github.frank.harness.ai.protocol.ToolResult.success("ok");
        assertThat(hook.afterToolCall(call, result)).isSameAs(result);
    }
}
```

- [ ] **步骤 2：运行测试**

```bash
mvn test -pl harness-core -Dtest=ApprovalHookTest -q
```

预期：Tests run: 7, Failures: 0

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/test/java/io/github/frank/harness/core/approval/
git commit -m "test: ApprovalHook — safe pass, dangerous block, approve/reject, timeout, empty patterns"
```

---

### 任务 13：DockerSandbox Mock 测试

**文件：**
- 创建：`harness-core/src/test/java/io/github/frank/harness/core/sandbox/DockerSandboxTest.java`

- [ ] **步骤 1：创建测试文件（简化版 — Mock 不完整，验证基础逻辑）**

由于本机无 Docker，仅测试 DockerClient 的 UnixSocketFactory 构造和 SandboxConfig 工厂方法：

```java
package io.github.frank.harness.core.sandbox;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerSandboxTest {

    @Test
    void sandboxConfigDocker() {
        var config = SandboxConfig.docker(Path.of("/tmp"), "ubuntu:22.04");
        assertThat(config.mode()).isEqualTo(SandboxConfig.SandboxMode.DOCKER);
        assertThat(config.dockerImage()).isEqualTo("ubuntu:22.04");
        assertThat(config.hostWorkdir()).isEqualTo(Path.of("/tmp"));
        assertThat(config.defaultTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(config.maxOutputBytes()).isEqualTo(1_048_576);
    }

    @Test
    void sandboxConfigLocal() {
        var config = SandboxConfig.local(Path.of("/tmp"));
        assertThat(config.mode()).isEqualTo(SandboxConfig.SandboxMode.LOCAL);
        assertThat(config.dockerImage()).isNull();
    }

    @Test
    void dockerClientConstructsWithSocketPath() {
        var client = new DockerClient("/var/run/docker.sock");
        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void dockerClientRejectsInvalidSocket() {
        var client = new DockerClient("/nonexistent/socket");
        assertThrows(RuntimeException.class, () -> {
            client.createContainer("ubuntu:22.04", "/tmp", "/workspace");
        });
        client.close();
    }

    // TODO: PRODUCTION — 真实 Docker 集成测试（TestContainers 或本地 daemon）
}
```

- [ ] **步骤 2：运行测试**

```bash
mvn test -pl harness-core -Dtest=DockerSandboxTest -q
```

预期：Tests run: 4, Failures: 0（createContainer 抛 RuntimeException 符合预期）

- [ ] **步骤 3：Commit**

```bash
git add harness-core/src/test/java/io/github/frank/harness/core/sandbox/DockerSandboxTest.java
git commit -m "test: DockerSandbox basic tests — config, client construction, invalid socket"
```

---

### 任务 14：全量编译 + 全量测试验证

- [ ] **步骤 1：全量编译**

```bash
cd /Users/sunhanbin/Documents/workspace/agent-harness && mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **步骤 2：运行所有测试**

```bash
mvn test -q
```

预期：Tests run: 18, Failures: 0, Errors: 0, Skipped: 0

- [ ] **步骤 3：验证文件清单**

```bash
git status
```

预期：working tree clean

- [ ] **步骤 4：Commit（如有未提交的变更）**

```bash
git add -A && git commit -m "chore: final verification — all tests pass, clean tree"
```

---

## 审查点

| 任务 | 审查内容 |
|------|---------|
| 1-3 | 接口定义正确，无循环依赖 |
| 4 | DockerSandbox close() 是否可重入 |
| 5 | pom 依赖不引入传递冲突 |
| 7 | ApprovalHook 超时后 pending queue 是否清理 |
| 9 | BashTool/ReadTool/WriteTool 参数签名一致 |
| 10 | HarnessCLI 线程安全（System.out + 审批轮询） |
| 11-13 | 测试覆盖核心逻辑和边界 |
