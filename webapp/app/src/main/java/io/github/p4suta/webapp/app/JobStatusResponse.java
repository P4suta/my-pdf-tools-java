package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.domain.Job;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The response body for a job-status query.
 *
 * @param jobId the job id
 * @param state the lifecycle state
 * @param createdAt when the job was accepted (ISO-8601)
 * @param finishedAt when it reached a terminal state (ISO-8601), or {@code null} while in flight
 * @param errorKind a stable error-kind token when failed, else {@code null}
 * @param errorMessage a human-readable failure description when failed, else {@code null}
 */
public record JobStatusResponse(
        String jobId,
        String state,
        String createdAt,
        @Nullable String finishedAt,
        @Nullable String errorKind,
        @Nullable String errorMessage) {

    /**
     * {@return a status response describing {@code job}}
     *
     * @param job the job to describe
     */
    public static JobStatusResponse from(Job job) {
        @Nullable Instant finishedAt = job.finishedAt();
        return new JobStatusResponse(
                job.id().value(),
                job.state().name(),
                job.createdAt().toString(),
                finishedAt != null ? finishedAt.toString() : null,
                job.errorKind(),
                job.errorMessage());
    }
}
