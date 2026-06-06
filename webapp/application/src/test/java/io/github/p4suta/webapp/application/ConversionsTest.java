package io.github.p4suta.webapp.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.github.p4suta.webapp.port.ConversionExecutor;
import io.github.p4suta.webapp.port.JobIdGenerator;
import io.github.p4suta.webapp.port.QueueFullException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversionsTest {

    private static final JobId ID = new JobId("job-1");
    private static final Instant NOW = Instant.ofEpochSecond(1000);
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    @TempDir Path tmp;

    private final FakeJobStore store = new FakeJobStore();
    private final FakeProgressPublisher publisher = new FakeProgressPublisher();
    private final JobIdGenerator ids = () -> ID;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private FakeWorkspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new FakeWorkspace(tmp);
    }

    private Conversions conversions(ConversionEngine engine, ConversionExecutor executor) {
        return new Conversions(store, engine, executor, workspace, publisher, ids, clock);
    }

    private static InputStream upload() {
        return new ByteArrayInputStream("%PDF-1.7".getBytes(UTF_8));
    }

    @Test
    void submitRunsToCompletionWithASynchronousExecutor() throws IOException {
        ConversionEngine engine =
                (req, in, out, sink) -> {
                    sink.emit(new ProgressEvent.RunStarted(1));
                    Files.writeString(out, "%PDF book");
                    sink.emit(new ProgressEvent.RunCompleted());
                };

        JobId id = conversions(engine, Runnable::run).submit(REQUEST, "scan.pdf", upload());

        assertThat(id).isEqualTo(ID);
        Job job = store.find(id).orElseThrow();
        assertThat(job.state()).isEqualTo(JobState.DONE);
        assertThat(job.finishedAt()).isEqualTo(NOW);
        assertThat(job.originalFilename()).isEqualTo("scan.pdf");
        assertThat(publisher.eventsFor(id))
                .containsExactly(new ProgressEvent.RunStarted(1), new ProgressEvent.RunCompleted());
        assertThat(publisher.closed).containsExactly(id);
        assertThat(Files.readString(workspace.inputPdf(id))).isEqualTo("%PDF-1.7");
    }

    @Test
    void getReturnsResultPathOnceDone() throws IOException {
        ConversionEngine engine =
                (req, in, out, sink) -> {
                    Files.writeString(out, "%PDF book");
                    sink.emit(new ProgressEvent.RunCompleted());
                };
        Conversions conversions = conversions(engine, Runnable::run);

        JobId id = conversions.submit(REQUEST, "scan.pdf", upload());

        assertThat(conversions.result(id)).isEqualTo(workspace.outputPdf(id));
        assertThat(Files.readString(conversions.result(id))).isEqualTo("%PDF book");
    }

    @Test
    void recordsTheFailureKindAndMessageFromTheEnginesEvent() throws IOException {
        ConversionEngine engine =
                (req, in, out, sink) -> {
                    sink.emit(new ProgressEvent.RunStarted(1));
                    sink.emit(new ProgressEvent.RunFailed("EXTRACT", "pdfimages not found"));
                    throw new IOException("pdfbook exited 1");
                };

        JobId id = conversions(engine, Runnable::run).submit(REQUEST, "scan.pdf", upload());

        Job job = store.find(id).orElseThrow();
        assertThat(job.state()).isEqualTo(JobState.FAILED);
        assertThat(job.errorKind()).isEqualTo("EXTRACT");
        assertThat(job.errorMessage()).isEqualTo("pdfimages not found");
        assertThat(publisher.closed).containsExactly(id);
    }

    @Test
    void synthesizesAFailureEventWhenTheEngineThrowsWithoutEmittingOne() throws IOException {
        ConversionEngine engine =
                (req, in, out, sink) -> {
                    throw new IOException("cannot start pdfbook");
                };

        JobId id = conversions(engine, Runnable::run).submit(REQUEST, "scan.pdf", upload());

        Job job = store.find(id).orElseThrow();
        assertThat(job.state()).isEqualTo(JobState.FAILED);
        assertThat(job.errorKind()).isEqualTo("IOException");
        assertThat(job.errorMessage()).isEqualTo("cannot start pdfbook");
        assertThat(publisher.eventsFor(id))
                .containsExactly(
                        new ProgressEvent.RunFailed("IOException", "cannot start pdfbook"));
    }

    @Test
    void fallsBackToTheExceptionClassNameWhenItsMessageIsNull() throws IOException {
        ConversionEngine engine =
                (req, in, out, sink) -> {
                    throw new IllegalStateException();
                };

        JobId id = conversions(engine, Runnable::run).submit(REQUEST, "scan.pdf", upload());

        Job job = store.find(id).orElseThrow();
        assertThat(job.errorKind()).isEqualTo("IllegalStateException");
        assertThat(job.errorMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void theTaskNoopsIfTheJobWasRemovedBeforeItRan() throws IOException {
        ConversionExecutor removeThenRun =
                task -> {
                    store.delete(ID);
                    task.run();
                };

        JobId id =
                conversions((req, in, out, sink) -> {}, removeThenRun)
                        .submit(REQUEST, "scan.pdf", upload());

        assertThat(store.find(id)).isEmpty();
        assertThat(publisher.closed).isEmpty();
        assertThat(publisher.eventsFor(id)).isEmpty();
    }

    @Test
    void getThrowsForAnUnknownJob() {
        Conversions conversions = conversions((req, in, out, sink) -> {}, Runnable::run);
        assertThatThrownBy(() -> conversions.get(new JobId("nope")))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessage("no such job: nope");
    }

    @Test
    void resultThrowsBeforeTheJobIsDone() throws IOException {
        // A deferred executor never runs the task, so the job stays QUEUED.
        Conversions conversions = conversions((req, in, out, sink) -> {}, task -> {});

        JobId id = conversions.submit(REQUEST, "scan.pdf", upload());

        assertThatThrownBy(() -> conversions.result(id))
                .isInstanceOf(ResultNotReadyException.class);
    }

    @Test
    void resultThrowsNotFoundWhenTheFileIsMissingDespiteDone() throws IOException {
        // The engine succeeds but writes no output, so the result is gone even though state is
        // DONE.
        Conversions conversions =
                conversions(
                        (req, in, out, sink) -> sink.emit(new ProgressEvent.RunCompleted()),
                        Runnable::run);

        JobId id = conversions.submit(REQUEST, "scan.pdf", upload());

        assertThat(store.find(id).orElseThrow().state()).isEqualTo(JobState.DONE);
        assertThatThrownBy(() -> conversions.result(id)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void rollsBackTheJobWhenTheQueueIsFull() throws IOException {
        Conversions conversions =
                conversions(
                        (req, in, out, sink) -> {},
                        task -> {
                            throw new QueueFullException("full");
                        });

        assertThatThrownBy(() -> conversions.submit(REQUEST, "scan.pdf", upload()))
                .isInstanceOf(QueueFullException.class);

        assertThat(store.find(ID)).isEmpty();
        assertThat(Files.exists(workspace.inputPdf(ID))).isFalse();
    }

    @Test
    void rollbackToleratesAWorkspaceRemovalFailure() throws IOException {
        workspace.failRemove = true;
        Conversions conversions =
                conversions(
                        (req, in, out, sink) -> {},
                        task -> {
                            throw new QueueFullException("full");
                        });

        assertThatThrownBy(() -> conversions.submit(REQUEST, "scan.pdf", upload()))
                .isInstanceOf(QueueFullException.class);

        assertThat(store.find(ID)).isEmpty();
    }
}
