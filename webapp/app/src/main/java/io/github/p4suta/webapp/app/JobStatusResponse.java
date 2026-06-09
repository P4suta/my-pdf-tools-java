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
 */
public record JobStatusResponse(
        String jobId,
        String state,
        String createdAt,
        @Nullable String finishedAt,
        @Nullable String errorKind) {

    /**
     * {@return a status response describing {@code job}}.
     *
     * <p>The failure {@code message} the Job carries is intentionally NOT exposed: it holds
     * server-only diagnostics (subprocess stderr, absolute paths). The client localizes from the
     * stable {@code errorKind} alone (presentation-free boundary); the detail stays in the server
     * log and the Job record. Mirrors the SSE boundary in {@code SseProgressPublisher}.
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
                job.errorKind());
    }
}
