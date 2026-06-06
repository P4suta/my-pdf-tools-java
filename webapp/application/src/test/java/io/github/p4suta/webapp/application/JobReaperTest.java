package io.github.p4suta.webapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobReaperTest {

    private static final Instant NOW = Instant.ofEpochSecond(10_000);
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    @TempDir Path tmp;

    private final FakeJobStore store = new FakeJobStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private FakeWorkspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new FakeWorkspace(tmp);
    }

    private JobReaper reaper() {
        return new JobReaper(store, workspace, clock, Duration.ofHours(1));
    }

    private void saveJob(JobId id, Instant createdAt) throws IOException {
        store.save(Job.queued(id, REQUEST, "x.pdf", createdAt));
        workspace.allocate(id);
        Files.writeString(workspace.inputPdf(id), "x");
    }

    @Test
    void removesExpiredJobsAndKeepsFreshOnes() throws IOException {
        JobId old = new JobId("old");
        JobId fresh = new JobId("fresh");
        saveJob(old, NOW.minus(Duration.ofHours(2)));
        saveJob(fresh, NOW);

        assertThat(reaper().reap()).containsExactly(old);
        assertThat(store.find(old)).isEmpty();
        assertThat(store.find(fresh)).isPresent();
        assertThat(Files.exists(workspace.inputPdf(old))).isFalse();
        assertThat(Files.exists(workspace.inputPdf(fresh))).isTrue();
    }

    @Test
    void reapsNothingWhenEveryJobIsFresh() throws IOException {
        saveJob(new JobId("fresh"), NOW);
        assertThat(reaper().reap()).isEmpty();
    }

    @Test
    void toleratesAWorkspaceRemovalFailure() throws IOException {
        JobId old = new JobId("old");
        workspace.failRemove = true;
        saveJob(old, NOW.minus(Duration.ofHours(2)));

        assertThat(reaper().reap()).containsExactly(old);
        assertThat(store.find(old)).isEmpty();
    }
}
