package io.github.frank.harness.core.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight Docker Engine API client built on OkHttp with Unix-domain-socket support.
 *
 * <p>Covers the subset of the Docker API needed by {@link DockerSandbox}:
 * container create / start / stop / remove and exec create / start / inspect.
 */
public class DockerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DockerClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String socketPath;

    public DockerClient(String socketPath) {
        this(socketPath, Duration.ofSeconds(30));
    }

    public DockerClient(String socketPath, Duration callTimeout) {
        this.socketPath = socketPath;
        this.httpClient = new OkHttpClient.Builder()
                .socketFactory(new UnixDomainSocketFactory(socketPath))
                .callTimeout(callTimeout)
                .build();
    }

    // ── Container lifecycle ──────────────────────────────────────

    /**
     * Create a container that sleeps indefinitely so we can exec into it later.
     *
     * @return container ID
     */
    public String createContainer(String image, String workDir,
                                  List<String> env, List<String> volumes,
                                  long memoryLimitMb, boolean networkDisabled) throws IOException {
        var hostConfig = mapper.createObjectNode();
        hostConfig.put("Memory", memoryLimitMb > 0 ? memoryLimitMb * 1024 * 1024 : 0);
        hostConfig.put("NetworkMode", networkDisabled ? "none" : "bridge");

        if (!volumes.isEmpty()) {
            var binds = hostConfig.putArray("Binds");
            for (var v : volumes) binds.add(v);
        }

        var config = mapper.createObjectNode();
        config.put("Image", image);
        config.put("WorkingDir", workDir);
        config.set("Cmd", mapper.createArrayNode().add("sleep").add("infinity"));
        config.set("HostConfig", hostConfig);

        var envArray = config.putArray("Env");
        for (var e : env) envArray.add(e);

        return post("/containers/create", config)
                .get("Id").asText();
    }

    public void startContainer(String containerId) throws IOException {
        post("/containers/" + containerId + "/start", null);
    }

    public void stopContainer(String containerId) throws IOException {
        post("/containers/" + containerId + "/stop", null);
    }

    public void removeContainer(String containerId) throws IOException {
        var request = new Request.Builder()
                .url("http://localhost/containers/" + containerId + "?force=true")
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Failed to remove container {}: {}", containerId, response.code());
            }
        }
    }

    // ── Exec ─────────────────────────────────────────────────────

    /**
     * Create an exec instance inside a running container.
     *
     * @return exec ID
     */
    public String execCreate(String containerId, String[] cmd, String workDir) throws IOException {
        var config = mapper.createObjectNode();
        config.put("AttachStdout", true);
        config.put("AttachStderr", true);
        var cmdArray = config.putArray("Cmd");
        for (String c : cmd) cmdArray.add(c);
        if (workDir != null) config.put("WorkingDir", workDir);

        return post("/containers/" + containerId + "/exec", config)
                .get("Id").asText();
    }

    /**
     * Start an exec and collect its output.
     * <p>
     * Uses {@code Detach: false} so the HTTP response body contains the
     * multiplexed stdout/stderr stream.
     */
    public ExecOutput execStart(String execId) throws IOException {
        var config = mapper.createObjectNode();
        config.put("Detach", false);
        config.put("Tty", false);

        var body = RequestBody.create(config.toString(), JSON);
        var request = new Request.Builder()
                .url("http://localhost/exec/" + execId + "/start")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Exec start failed: HTTP " + response.code());
            }
            var responseBody = response.body();
            byte[] raw = responseBody != null ? responseBody.bytes() : new byte[0];
            return parseMultiplexed(raw);
        }
    }

    /** Inspect an exec instance to get its exit code. */
    public ExecInspect execInspect(String execId) throws IOException {
        var request = new Request.Builder()
                .url("http://localhost/exec/" + execId + "/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Exec inspect failed: HTTP " + response.code());
            }
            var body = response.body() != null ? response.body().string() : "{}";
            JsonNode json = mapper.readTree(body);
            return new ExecInspect(json.get("ExitCode").asInt(), json.get("Running").asBoolean());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private JsonNode post(String path, ObjectNode config) throws IOException {
        String jsonBody = config != null ? config.toString() : "{}";
        var body = RequestBody.create(jsonBody, JSON);
        var request = new Request.Builder()
                .url("http://localhost" + path)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("Docker API error " + path + " (" + response.code() + "): " + responseBody);
            }
            return mapper.readTree(responseBody);
        }
    }

    /**
     * Parse the Docker multiplexed stream format.
     * <pre>
     * [stream-type:1][0x00][0x00][0x00][frame-size:4][payload:frame-size]
     * stream-type: 1=stdout, 2=stderr
     * </pre>
     */
    static ExecOutput parseMultiplexed(byte[] data) {
        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        int pos = 0;
        while (pos + 8 <= data.length) {
            int streamType = data[pos] & 0xFF;
            int frameSize = ((data[pos + 4] & 0xFF) << 24)
                          | ((data[pos + 5] & 0xFF) << 16)
                          | ((data[pos + 6] & 0xFF) << 8)
                          |  (data[pos + 7] & 0xFF);
            pos += 8;
            if (pos + frameSize > data.length) break;

            String frame = new String(data, pos, frameSize, StandardCharsets.UTF_8);
            if (streamType == 1) {
                stdout.append(frame);
            } else if (streamType == 2) {
                stderr.append(frame);
            }
            pos += frameSize;
        }
        return new ExecOutput(stdout.toString(), stderr.toString());
    }

    @Override
    public void close() {
        // OkHttpClient manages its own connection pool; nothing to tear down.
    }

    // ── Value types ──────────────────────────────────────────────

    public record ExecOutput(String stdout, String stderr) {}

    public record ExecInspect(int exitCode, boolean running) {}

    // ── Unix-domain socket support for OkHttp ───────────────────

    /**
     * SocketFactory that produces Unix-domain sockets connected to {@code path}.
     *
     * <p>Returns a thin wrapper around {@link SocketChannel#socket()} so that
     * OkHttp's {@code connect()} call is a no-op (the channel is already
     * connected). All I/O is delegated to the underlying channel socket.
     */
    private static class UnixDomainSocketFactory extends SocketFactory {

        private final String path;

        UnixDomainSocketFactory(String path) {
            this.path = path;
        }

        @Override
        public Socket createSocket() throws IOException {
            var addr = UnixDomainSocketAddress.of(path);
            var channel = SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            channel.connect(addr);
            return new UnixSocket(channel);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return createSocket();
        }

        @Override
        public Socket createSocket(String host, int port,
                                   InetAddress localHost, int localPort) throws IOException {
            return createSocket();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return createSocket();
        }

        @Override
        public Socket createSocket(InetAddress address, int port,
                                   InetAddress localAddress, int localPort) throws IOException {
            return createSocket();
        }
    }

    /**
     * Socket wrapper that delegates everything to a pre-connected
     * {@link SocketChannel} socket. The {@link #connect(SocketAddress)} override
     * is a no-op because OkHttp always calls it — even on already-connected
     * sockets returned by a custom {@link SocketFactory}.
     */
    private static class UnixSocket extends Socket {

        private final Socket delegate;

        UnixSocket(SocketChannel channel) {
            this.delegate = channel.socket();
        }

        // Block OkHttp's connect() — channel is already connected.
        @Override
        public void connect(SocketAddress endpoint) { /* no-op */ }

        @Override
        public void connect(SocketAddress endpoint, int timeout) { /* no-op */ }

        // ── Full delegation to the NIO socket ──────────────────
        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public synchronized void close() throws IOException { delegate.close(); }

        @Override
        public boolean isConnected() { return delegate.isConnected(); }

        @Override
        public boolean isClosed() { return delegate.isClosed(); }

        @Override
        public void setSoTimeout(int timeout) throws java.net.SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws java.net.SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws java.net.SocketException {
            delegate.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws java.net.SocketException {
            return delegate.getTcpNoDelay();
        }

        @Override
        public void setKeepAlive(boolean on) throws java.net.SocketException {
            delegate.setKeepAlive(on);
        }

        @Override
        public boolean getKeepAlive() throws java.net.SocketException {
            return delegate.getKeepAlive();
        }

        @Override
        public void shutdownInput() throws IOException { delegate.shutdownInput(); }

        @Override
        public void shutdownOutput() throws IOException { delegate.shutdownOutput(); }
    }
}
