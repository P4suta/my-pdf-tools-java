package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.application.JobReaper;
import io.github.p4suta.webapp.domain.JobId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically sweeps expired jobs and drops each one's SSE buffer, on the schedule {@code
 * app.reaper-interval-ms}.
 */
@Component
public class ReaperJob {

    private final JobReaper reaper;
    private final SseProgressPublisher publisher;

    /**
     * @param reaper removes expired jobs and their files
     * @param publisher holds the SSE buffers to drop alongside
     */
    public ReaperJob(JobReaper reaper, SseProgressPublisher publisher) {
        this.reaper = reaper;
        this.publisher = publisher;
    }

    /** Removes expired jobs and forgets their SSE streams. */
    @Scheduled(fixedDelayString = "${app.reaper-interval-ms}")
    public void reap() {
        for (JobId id : reaper.reap()) {
            publisher.forget(id);
        }
    }
}
