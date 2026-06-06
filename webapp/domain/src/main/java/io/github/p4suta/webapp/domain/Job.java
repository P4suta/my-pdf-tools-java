package io.github.p4suta.webapp.domain;

import io.github.p4suta.shared.kernel.Validators;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One conversion job and its lifecycle. Immutable: each transition returns a new instance, and only
 * the legal transitions ({@code QUEUED -> RUNNING -> DONE | FAILED}) are allowed; an illegal one
 * throws. The error fields are populated only in {@link JobState#FAILED}; {@code finishedAt} only
 * once terminal.
 *
 * @param id the job's identity
 * @param state the current lifecycle state
 * @param request the conversion options
 * @param originalFilename the uploaded file's name, used to name the download
 * @param createdAt when the job was accepted
 * @param finishedAt when the job reached a terminal state, or {@code null} while in flight
 * @param errorKind a stable error-kind token when {@code FAILED}, else {@code null}
 * @param errorMessage a human-readable failure description when {@code FAILED}, else {@code null}
 */
public record Job(
        JobId id,
        JobState state,
        ConversionRequest request,
        String originalFilename,
        Instant createdAt,
        @Nullable Instant finishedAt,
        @Nullable String errorKind,
        @Nullable String errorMessage) {

    public Job {
        Validators.requireNonNull(id, "id");
        Validators.requireNonNull(state, "state");
        Validators.requireNonNull(request, "request");
        Validators.requireNonNull(originalFilename, "originalFilename");
        Validators.requireNonNull(createdAt, "createdAt");
    }

    /**
     * {@return a fresh {@link JobState#QUEUED} job}
     *
     * @param id the job's identity
     * @param request the conversion options
     * @param originalFilename the uploaded file's name
     * @param createdAt when the job was accepted
     */
    public static Job queued(
            JobId id, ConversionRequest request, String originalFilename, Instant createdAt) {
        return new Job(id, JobState.QUEUED, request, originalFilename, createdAt, null, null, null);
    }

    /**
     * {@return this job moved to {@link JobState#RUNNING}}
     *
     * @throws IllegalStateException if this job is not currently {@link JobState#QUEUED}
     */
    public Job toRunning() {
        requireState(JobState.QUEUED);
        return new Job(
                id, JobState.RUNNING, request, originalFilename, createdAt, null, null, null);
    }

    /**
     * {@return this job moved to {@link JobState#DONE}}
     *
     * @param finishedAt when the conversion completed
     * @throws IllegalStateException if this job is not currently {@link JobState#RUNNING}
     */
    public Job toDone(Instant finishedAt) {
        requireState(JobState.RUNNING);
        return new Job(
                id, JobState.DONE, request, originalFilename, createdAt, finishedAt, null, null);
    }

    /**
     * {@return this job moved to {@link JobState#FAILED}, recording the error}
     *
     * @param finishedAt when the conversion failed
     * @param errorKind a stable error-kind token
     * @param errorMessage a human-readable failure description
     * @throws IllegalStateException if this job is already terminal
     */
    public Job toFailed(Instant finishedAt, String errorKind, String errorMessage) {
        if (state.isTerminal()) {
            throw new IllegalStateException("job " + id.value() + " is already " + state);
        }
        return new Job(
                id,
                JobState.FAILED,
                request,
                originalFilename,
                createdAt,
                finishedAt,
                errorKind,
                errorMessage);
    }

    private void requireState(JobState expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "job " + id.value() + " is " + state + ", expected " + expected);
        }
    }
}
