package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobTest {

    private static final JobId ID = new JobId("job-1");
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 4);
    private static final Instant CREATED = Instant.ofEpochSecond(1000);
    private static final Instant FINISHED = Instant.ofEpochSecond(2000);

    private static Job queued() {
        return Job.queued(ID, REQUEST, "scan.pdf", CREATED);
    }

    @Test
    void queuedStartsInQueuedWithNoTerminalFields() {
        Job job = queued();
        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.id()).isEqualTo(ID);
        assertThat(job.request()).isEqualTo(REQUEST);
        assertThat(job.originalFilename()).isEqualTo("scan.pdf");
        assertThat(job.createdAt()).isEqualTo(CREATED);
        assertThat(job.finishedAt()).isNull();
        assertThat(job.errorKind()).isNull();
        assertThat(job.errorMessage()).isNull();
    }

    @Test
    void runsThenSucceeds() {
        Job done = queued().toRunning().toDone(FINISHED);
        assertThat(done.state()).isEqualTo(JobState.DONE);
        assertThat(done.finishedAt()).isEqualTo(FINISHED);
        assertThat(done.errorKind()).isNull();
        assertThat(done.errorMessage()).isNull();
    }

    @Test
    void runsThenFails() {
        Job failed = queued().toRunning().toFailed(FINISHED, "IO", "boom");
        assertThat(failed.state()).isEqualTo(JobState.FAILED);
        assertThat(failed.finishedAt()).isEqualTo(FINISHED);
        assertThat(failed.errorKind()).isEqualTo("IO");
        assertThat(failed.errorMessage()).isEqualTo("boom");
    }

    @Test
    void canFailDirectlyFromQueued() {
        Job failed = queued().toFailed(FINISHED, "TIMEOUT", "gave up");
        assertThat(failed.state()).isEqualTo(JobState.FAILED);
        assertThat(failed.errorKind()).isEqualTo("TIMEOUT");
    }

    @Test
    void toRunningRejectsNonQueued() {
        Job running = queued().toRunning();
        assertThatThrownBy(running::toRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("job job-1 is RUNNING, expected QUEUED");
    }

    @Test
    void toDoneRejectsNonRunning() {
        Job queued = queued();
        assertThatThrownBy(() -> queued.toDone(FINISHED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("job job-1 is QUEUED, expected RUNNING");
    }

    @Test
    void toFailedRejectsAlreadyTerminal() {
        Job done = queued().toRunning().toDone(FINISHED);
        assertThatThrownBy(() -> done.toFailed(FINISHED, "IO", "late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("job job-1 is already DONE");
    }
}
