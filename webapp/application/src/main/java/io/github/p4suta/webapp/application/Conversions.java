package io.github.p4suta.webapp.application;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.github.p4suta.webapp.port.ConversionExecutor;
import io.github.p4suta.webapp.port.JobIdGenerator;
import io.github.p4suta.webapp.port.JobStore;
import io.github.p4suta.webapp.port.ProgressPublisher;
import io.github.p4suta.webapp.port.QueueFullException;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The conversion use cases. {@link #submit} accepts an upload, allocates the job's workspace,
 * stores the bytes, records a {@code QUEUED} job, and hands the conversion task to the executor;
 * the task marks the job {@code RUNNING}, drives the {@link ConversionEngine}, routes every
 * progress event to the {@link ProgressPublisher}, and records the terminal {@code DONE}/{@code
 * FAILED} state. {@link #get} and {@link #result} answer status and download lookups.
 *
 * <p>Framework-free and synchronous from this class's point of view: all asynchrony lives behind
 * the {@link ConversionExecutor} port, so a test with a synchronous executor exercises the whole
 * task inline.
 */
public final class Conversions {

    private static final Logger log = LoggerFactory.getLogger(Conversions.class);

    private final JobStore store;
    private final ConversionEngine engine;
    private final ConversionExecutor executor;
    private final Workspace workspace;
    private final ProgressPublisher publisher;
    private final JobIdGenerator ids;
    private final Clock clock;

    /**
     * @param store the job store
     * @param engine runs pdfbook
     * @param executor runs conversion tasks under bounded concurrency
     * @param workspace owns each job's files
     * @param publisher fans progress out to subscribers
     * @param ids mints job ids
     * @param clock the time source
     */
    public Conversions(
            JobStore store,
            ConversionEngine engine,
            ConversionExecutor executor,
            Workspace workspace,
            ProgressPublisher publisher,
            JobIdGenerator ids,
            Clock clock) {
        this.store = store;
        this.engine = engine;
        this.executor = executor;
        this.workspace = workspace;
        this.publisher = publisher;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Accepts an upload and queues a conversion.
     *
     * @param request the conversion options
     * @param originalFilename the uploaded file's name
     * @param pdf the uploaded bytes (consumed by this call)
     * @return the new job's id
     * @throws IOException if the upload cannot be stored
     * @throws QueueFullException if the executor is at capacity (the partial job is rolled back)
     */
    public JobId submit(ConversionRequest request, String originalFilename, InputStream pdf)
            throws IOException {
        JobId id = ids.next();
        workspace.allocate(id);
        workspace.storeUpload(id, pdf);
        store.save(Job.queued(id, request, originalFilename, clock.instant()));
        try {
            executor.submit(() -> runConversion(id, request));
        } catch (QueueFullException e) {
            store.delete(id);
            removeQuietly(id);
            throw e;
        }
        return id;
    }

    /**
     * {@return the job with id {@code id}}
     *
     * @param id the job id
     * @throws JobNotFoundException if no such job exists
     */
    public Job get(JobId id) {
        return store.find(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    /**
     * {@return the path of {@code id}'s finished result}
     *
     * @param id the job id
     * @throws JobNotFoundException if no such job exists, or its result has been reaped
     * @throws ResultNotReadyException if the job has not reached {@link JobState#DONE}
     */
    public Path result(JobId id) {
        Job job = get(id);
        if (job.state() != JobState.DONE) {
            throw new ResultNotReadyException(id, job.state());
        }
        return workspace.resultIfPresent(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    private void runConversion(JobId id, ConversionRequest request) {
        @Nullable Job queued = store.find(id).orElse(null);
        if (queued == null) {
            return; // reaped or removed before the worker picked it up
        }
        Job running = queued.toRunning();
        store.save(running);

        AtomicReference<ProgressEvent.@Nullable RunFailed> failure = new AtomicReference<>();
        ProgressSink sink =
                event -> {
                    publisher.publish(id, event);
                    if (event instanceof ProgressEvent.RunFailed f) {
                        failure.set(f);
                    }
                };
        try {
            engine.convert(request, workspace.inputPdf(id), workspace.outputPdf(id), sink);
            store.save(running.toDone(clock.instant()));
        } catch (IOException | RuntimeException e) {
            log.warn("conversion {} failed: {}", id.value(), e.getMessage());
            ProgressEvent.@Nullable RunFailed reported = failure.get();
            String kind = reported != null ? reported.kind() : e.getClass().getSimpleName();
            String message = reported != null ? reported.message() : messageOf(e);
            store.save(running.toFailed(clock.instant(), kind, message));
            if (reported == null) {
                // The engine failed before emitting a terminal event; synthesize one so subscribers
                // still see the failure.
                publisher.publish(id, new ProgressEvent.RunFailed(kind, message));
            }
        } finally {
            publisher.close(id);
        }
    }

    private void removeQuietly(JobId id) {
        try {
            workspace.remove(id);
        } catch (IOException e) {
            log.warn("could not clean up workspace for {}: {}", id.value(), e.getMessage());
        }
    }

    private static String messageOf(Throwable e) {
        @Nullable String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
