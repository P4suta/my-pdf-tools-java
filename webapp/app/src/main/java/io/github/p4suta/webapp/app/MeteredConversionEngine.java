package io.github.p4suta.webapp.app;

import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ConversionEngine} decorator that times each conversion and tags its outcome. The job's
 * {@code DONE}/{@code FAILED} state in {@code Conversions} is decided by whether {@code convert}
 * returns or throws, so this {@code outcome=success|failure} tag agrees with the {@code
 * pdfbook.jobs.by_state} gauge. Lives in the app layer so Micrometer never reaches the
 * framework-free core; the timer's count doubles as the success/failure counter.
 */
final class MeteredConversionEngine implements ConversionEngine {

    private final ConversionEngine delegate;
    private final Timer success;
    private final Timer failure;

    MeteredConversionEngine(ConversionEngine delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.success = timer(registry, "success");
        this.failure = timer(registry, "failure");
    }

    private static Timer timer(MeterRegistry registry, String outcome) {
        return Timer.builder("pdfbook.conversion.duration")
                .description("pdfbook conversion wall-clock time")
                .tag("outcome", outcome)
                .register(registry);
    }

    @Override
    public void convert(
            ConversionRequest request, Path inputPdf, Path outputPdf, ProgressSink progress)
            throws IOException {
        long start = System.nanoTime();
        try {
            delegate.convert(request, inputPdf, outputPdf, progress);
            success.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (IOException | RuntimeException e) {
            failure.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            throw e;
        }
    }
}
