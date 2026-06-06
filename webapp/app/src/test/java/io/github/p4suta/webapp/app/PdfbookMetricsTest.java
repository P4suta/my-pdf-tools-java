package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.infrastructure.InMemoryJobStore;
import io.github.p4suta.webapp.port.JobStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PdfbookMetricsTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 1);

    @Test
    void registersQueueAndJobStateGauges() {
        JobStore store = new InMemoryJobStore();
        store.save(Job.queued(new JobId("a"), REQUEST, "a.pdf", Instant.EPOCH));
        store.save(Job.queued(new JobId("b"), REQUEST, "b.pdf", Instant.EPOCH));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // Micrometer gauges keep only a weak reference to their source, so the binder and the job
        // store must stay strongly reachable through the assertions or a GC turns the gauges to
        // NaN.
        // (In production the binder is a long-lived Spring bean; the keep-alive assertions at the
        // end
        // are a test-only need.)
        PdfbookMetrics metrics = new PdfbookMetrics(new FakeQueueStats(3, 1, 4, 1, 0), store);
        metrics.bindTo(registry);

        assertThat(gauge(registry, "pdfbook.jobs.queued")).isEqualTo(3.0);
        assertThat(gauge(registry, "pdfbook.jobs.active")).isEqualTo(1.0);
        assertThat(gauge(registry, "pdfbook.queue.capacity")).isEqualTo(4.0);
        // (capacity - remaining) / capacity = (4 - 1) / 4
        assertThat(gauge(registry, "pdfbook.queue.saturation")).isEqualTo(0.75);
        assertThat(byState(registry, "QUEUED")).isEqualTo(2.0);
        assertThat(byState(registry, "DONE")).isEqualTo(0.0);

        assertThat(metrics).isNotNull();
        assertThat(store.all()).hasSize(2);
    }

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }

    private static double byState(SimpleMeterRegistry registry, String state) {
        return registry.get("pdfbook.jobs.by_state").tag("state", state).gauge().value();
    }
}
