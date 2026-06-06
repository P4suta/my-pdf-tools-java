package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MeteredConversionEngineTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 1);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void recordsASuccessTimingWhenTheDelegateReturns() throws IOException {
        ConversionEngine engine = new MeteredConversionEngine((req, in, out, sink) -> {}, registry);

        engine.convert(REQUEST, Path.of("in.pdf"), Path.of("out.pdf"), event -> {});

        assertThat(count("success")).isEqualTo(1);
        assertThat(count("failure")).isZero();
    }

    @Test
    void recordsAFailureTimingAndRethrowsWhenTheDelegateThrows() {
        ConversionEngine engine =
                new MeteredConversionEngine(
                        (req, in, out, sink) -> {
                            throw new IOException("boom");
                        },
                        registry);

        assertThatThrownBy(
                        () -> engine.convert(REQUEST, Path.of("in"), Path.of("out"), event -> {}))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        assertThat(count("failure")).isEqualTo(1);
        assertThat(count("success")).isZero();
    }

    private long count(String outcome) {
        return registry.get("pdfbook.conversion.duration").tag("outcome", outcome).timer().count();
    }
}
