package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.port.JobStore;
import io.github.p4suta.webapp.port.QueueStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Registers the pdfbook gauges. Two complementary lenses, not duplicates: {@code queued}/{@code
 * active}/{@code saturation} read the executor's {@link QueueStats} (the queue-depth view), while
 * {@code jobs.by_state} reads the {@link JobStore} (the domain-state view, which also counts
 * finished-but-not-yet-reaped jobs). Lives in the app layer so Micrometer stays out of the core.
 */
final class PdfbookMetrics implements MeterBinder {

    private final QueueStats queue;
    private final JobStore store;

    PdfbookMetrics(QueueStats queue, JobStore store) {
        this.queue = queue;
        this.store = store;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("pdfbook.jobs.queued", queue, QueueStats::queued)
                .description("conversions waiting in the queue")
                .register(registry);
        Gauge.builder("pdfbook.jobs.active", queue, QueueStats::active)
                .description("conversions running right now")
                .register(registry);
        Gauge.builder("pdfbook.queue.capacity", queue, QueueStats::capacity)
                .description("conversion queue capacity")
                .register(registry);
        Gauge.builder("pdfbook.queue.saturation", queue, PdfbookMetrics::saturation)
                .description("fraction of the queue currently occupied (0..1)")
                .register(registry);
        for (JobState state : JobState.values()) {
            Gauge.builder("pdfbook.jobs.by_state", store, s -> countByState(s, state))
                    .tag("state", state.name())
                    .description("jobs currently in each lifecycle state")
                    .register(registry);
        }
    }

    private static double saturation(QueueStats queue) {
        int capacity = queue.capacity();
        return capacity == 0 ? 0.0 : (double) (capacity - queue.remainingCapacity()) / capacity;
    }

    private static double countByState(JobStore store, JobState state) {
        return store.all().stream().filter(job -> job.state() == state).count();
    }
}
