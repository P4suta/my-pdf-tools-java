package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.port.QueueStats;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Always {@code UP} — a full queue is normal back-pressure (the API answers 429), not a failure —
 * but surfaces the live queue/worker counts as health details for operators.
 */
final class QueueHealthIndicator implements HealthIndicator {

    private final QueueStats queue;

    QueueHealthIndicator(QueueStats queue) {
        this.queue = queue;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("queued", queue.queued())
                .withDetail("active", queue.active())
                .withDetail("capacity", queue.capacity())
                .withDetail("remainingCapacity", queue.remainingCapacity())
                .build();
    }
}
