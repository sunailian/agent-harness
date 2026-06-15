package io.github.frank.harness.core.sandbox;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerSandboxTest {

    @Test void sandboxConfigDocker() {
        var config = SandboxConfig.docker("ubuntu:22.04", Path.of("/tmp"));
        assertThat(config.isDocker()).isTrue();
        assertThat(config.image()).isEqualTo("ubuntu:22.04");
    }

    @Test void sandboxConfigLocal() {
        var config = SandboxConfig.local(Path.of("/tmp"));
        assertThat(config.isDocker()).isFalse();
        assertThat(config.image()).isNull();
    }

    @Test void dockerClientRejectsInvalidSocket() {
        var client = new DockerClient("/nonexistent/nope.sock");
        assertThrows(Exception.class, () -> {
            client.createContainer("ubuntu:22.04", "/tmp", List.of(),
                List.of(), 0, false);
        });
        client.close();
    }
}
