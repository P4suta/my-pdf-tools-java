package io.github.p4suta.webapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryJobStoreTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    private final InMemoryJobStore store = new InMemoryJobStore();

    private static Job job(String id) {
        return Job.queued(new JobId(id), REQUEST, "x.pdf", Instant.EPOCH);
    }

    @Test
    void savesFindsAndDeletes() {
        Job job = job("a");
        store.save(job);
        assertThat(store.find(new JobId("a"))).contains(job);

        store.delete(new JobId("a"));
        assertThat(store.find(new JobId("a"))).isEmpty();
    }

    @Test
    void findingAMissingJobIsEmpty() {
        assertThat(store.find(new JobId("nope"))).isEmpty();
    }

    @Test
    void allReturnsASnapshotOfEveryJob() {
        store.save(job("a"));
        store.save(job("b"));
        assertThat(store.all())
                .hasSize(2)
                .extracting(Job::id)
                .contains(new JobId("a"), new JobId("b"));
    }
}
