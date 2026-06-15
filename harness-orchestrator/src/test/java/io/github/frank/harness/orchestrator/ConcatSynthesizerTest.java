package io.github.frank.harness.orchestrator;

import io.github.frank.harness.orchestrator.common.WorkerResult;
import io.github.frank.harness.orchestrator.orchestrator.synthesize.ConcatSynthesizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConcatSynthesizerTest {
    private final ConcatSynthesizer synth = new ConcatSynthesizer();

    @Test void multipleSuccesses() {
        var results = List.of(WorkerResult.success("t1", "Done A"), WorkerResult.success("t2", "Done B"));
        String out = synth.synthesize("Test goal", results);
        assertThat(out).contains("Done A", "Done B", "2/2");
    }

    @Test void mixedResults() {
        var results = List.of(WorkerResult.success("t1", "OK"), WorkerResult.error("t2", "Boom"));
        String out = synth.synthesize("Goal", results);
        assertThat(out).contains("OK", "FAILED", "1/2");
    }

    @Test void allFailed() {
        var results = List.of(WorkerResult.timeout("t1"), WorkerResult.error("t2", "err"));
        String out = synth.synthesize("Goal", results);
        assertThat(out).contains("0/2");
    }

    @Test void emptyResults() {
        String out = synth.synthesize("Goal", List.of());
        assertThat(out).contains("Goal");
    }
}
