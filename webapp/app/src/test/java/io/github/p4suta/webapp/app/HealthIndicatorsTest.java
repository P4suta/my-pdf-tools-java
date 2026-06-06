package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class HealthIndicatorsTest {

    @Test
    void pdfbookBinaryIsUpWhenExecutable() {
        Health health = new PdfbookBinaryHealthIndicator(Path.of("/bin/true")).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void pdfbookBinaryIsDownWhenMissing() {
        Health health = new PdfbookBinaryHealthIndicator(Path.of("/nonexistent/pdfbook")).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void workDirIsUpWhenWritable(@TempDir Path dir) {
        Health health = new WorkDirHealthIndicator(dir).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void workDirIsDownWhenMissing() {
        Health health = new WorkDirHealthIndicator(Path.of("/nonexistent/jobs")).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void queueIsAlwaysUpAndExposesCounts() {
        Health health = new QueueHealthIndicator(new FakeQueueStats(2, 1, 4, 1, 7)).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("queued", 2)
                .containsEntry("active", 1)
                .containsEntry("capacity", 4)
                .containsEntry("remainingCapacity", 1);
    }
}
