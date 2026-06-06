package io.github.p4suta.webapp.application;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.webapp.domain.CacheKey;
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
import io.github.p4suta.webapp.port.ResultCache;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    private final ResultCache cache;
    private final ProgressPublisher publisher;
    private final JobIdGenerator ids;
    private final Clock clock;

    /**
     * @param store the job store
     * @param engine runs pdfbook
     * @param executor runs conversion tasks under bounded concurrency
     * @param workspace owns each job's files
     * @param cache reuses a previous identical conversion's output
     * @param publisher fans progress out to subscribers
     * @param ids mints job ids
     * @param clock the time source
     */
    public Conversions(
            JobStore store,
            ConversionEngine engine,
            ConversionExecutor executor,
            Workspace workspace,
            ResultCache cache,
            ProgressPublisher publisher,
            JobIdGenerator ids,
            Clock clock) {
        this.store = store;
        this.engine = engine;
        this.executor = executor;
        this.workspace = workspace;
        this.cache = cache;
        this.publisher = publisher;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Accepts an upload and queues a conversion. If an identical conversion (same input bytes, same
     * options) is already cached, the job is completed from the cache without touching the queue or
     * pdfbook.
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
        String sha;
        try {
            sha = workspace.storeUpload(id, pdf);
        } catch (IOException | RuntimeException e) {
            removeQuietly(id); // a rejected or failed upload leaves no orphan working directory
            throw e;
        }
        CacheKey key = CacheKey.of(sha, request);
        store.save(Job.queued(id, request, originalFilename, clock.instant()));
        if (completeFromCache(id, key)) {
            return id; // identical conversion already cached — no queue, no pdfbook
        }
        try {
            executor.submit(() -> runConversion(id, request, key));
        } catch (QueueFullException e) {
            store.delete(id);
            removeQuietly(id);
            throw e;
        }
        return id;
    }

    /**
     * Checks the result cache for an already-converted output without an upload: given the client's
     * SHA-256 of the bytes and the same options it would submit, a hit mints a ready ({@code DONE})
     * job and returns its id — letting the client skip uploading the file. A miss returns empty.
     *
     * @param inputSha256 the lowercase-hex SHA-256 of the PDF the client holds
     * @param request the conversion options
     * @param originalFilename the upload's name, used to name the download
     * @return the id of a ready job if the result was cached, else empty
     * @throws IOException if a hit's result cannot be materialized
     */
    public Optional<JobId> probe(
            String inputSha256, ConversionRequest request, String originalFilename)
            throws IOException {
        CacheKey key = CacheKey.of(inputSha256, request);
        if (cache.find(key).isEmpty()) {
            return Optional.empty();
        }
        JobId id = ids.next();
        workspace.allocate(id);
        store.save(Job.queued(id, request, originalFilename, clock.instant()));
        if (!completeFromCache(id, key)) {
            // Evicted between the probe and now, or the result could not be placed: clean up and
            // report a miss so the client uploads normally.
            store.delete(id);
            removeQuietly(id);
            return Optional.empty();
        }
        return Optional.of(id);
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

    private void runConversion(JobId id, ConversionRequest request, CacheKey key) {
        // Bind the job id to the worker thread's MDC for the whole conversion so every log line
        // (the
        // engine's included) carries the correlation id. try-with-resources removes it on every
        // exit,
        // so the next job on this reused worker thread never inherits a stale id. (`_` is the
        // unnamed
        // resource — it is only held for its close().)
        try (var _ = MDC.putCloseable("jobId", id.value())) {
            @Nullable Job queued = store.find(id).orElse(null);
            if (queued == null) {
                return; // reaped or removed before the worker picked it up
            }
            Job running = queued.toRunning();
            store.save(running);

            // Dedup for free: an identical job that queued ahead of this one may have populated the
            // cache while it waited (a single worker runs them sequentially), so reuse that output
            // instead of invoking pdfbook again. completeFromCache also closes the SSE stream.
            if (completeFromCache(id, key)) {
                return;
            }

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
                storeInCache(key, id); // make this result reusable by future identical jobs
            } catch (IOException | RuntimeException e) {
                log.warn("conversion {} failed: {}", id.value(), e.getMessage());
                ProgressEvent.@Nullable RunFailed reported = failure.get();
                String kind = reported != null ? reported.kind() : e.getClass().getSimpleName();
                String message = reported != null ? reported.message() : messageOf(e);
                store.save(running.toFailed(clock.instant(), kind, message));
                if (reported == null) {
                    // The engine failed before emitting a terminal event; synthesize one so
                    // subscribers still see the failure.
                    publisher.publish(id, new ProgressEvent.RunFailed(kind, message));
                }
            } finally {
                publisher.close(id);
            }
        }
    }

    /**
     * If {@code key} is cached, materializes the result for {@code id}, marks the job {@code DONE},
     * and publishes a terminal event (closing the SSE stream). Works whether the job is still
     * {@code QUEUED} (the submit/probe fast path) or already {@code RUNNING} (the worker's dedup
     * re-check).
     *
     * @return whether the cache was hit and the job completed
     */
    private boolean completeFromCache(JobId id, CacheKey key) {
        Optional<Path> cached = cache.find(key);
        if (cached.isEmpty()) {
            return false;
        }
        @Nullable Job job = store.find(id).orElse(null);
        if (job == null) {
            return false; // reaped meanwhile
        }
        try {
            workspace.placeResult(id, cached.get());
        } catch (IOException e) {
            log.warn("cache hit but could not place result for {}: {}", id.value(), e.getMessage());
            return false; // fall back to a normal run
        }
        Job running = job.state() == JobState.QUEUED ? job.toRunning() : job;
        store.save(running.toDone(clock.instant()));
        // Synthesize a terminal event so any SSE subscriber sees completion; the publisher buffers
        // it (and openStream synthesizes one from a DONE job even if the buffer was dropped), so a
        // client subscribing just after submit still replays it.
        publisher.publish(id, new ProgressEvent.RunCompleted());
        publisher.close(id);
        return true;
    }

    private void storeInCache(CacheKey key, JobId id) {
        try {
            cache.store(key, workspace.outputPdf(id));
        } catch (IOException e) {
            // Caching is best-effort: the result is already produced and served from the workspace.
            log.warn("could not cache result for {}: {}", id.value(), e.getMessage());
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
