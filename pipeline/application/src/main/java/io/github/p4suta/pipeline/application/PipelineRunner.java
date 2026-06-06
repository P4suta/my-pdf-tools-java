package io.github.p4suta.pipeline.application;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.shared.observability.ExceptionMapper;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one pipeline run as pipes-and-filters: a {@link Source} opens the initial {@link Corpus},
 * each {@link Stage} transforms it in turn, and a {@link Sink} writes the single output. Every
 * intermediate lives in a per-run temp subdirectory removed when the run ends, so the only durable
 * artifacts are the source the Source reads and the one file the Sink writes — no intermediate
 * PDFs.
 *
 * <p>Pure orchestration: it constructs no adapters and touches no PDF or image engine, so it is
 * exercised with fakes. Each stage gets its own numbered subdirectory ({@code 00-source}, {@code
 * 01-despeckle}, {@code 02-register}, …) so a run's intermediates are inspectable while it runs and
 * self-cleaning afterwards.
 */
public final class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    /**
     * Runs {@code source -> stages -> sink} writing to {@code output} with no progress reporting:
     * {@link #run(Source, List, Sink, Path, ProgressSink)} with {@link ProgressSink#NO_OP}.
     *
     * @throws IOException if any step fails
     */
    public void run(Source source, List<Stage> stages, Sink sink, Path output) throws IOException {
        run(source, stages, sink, output, ProgressSink.NO_OP);
    }

    /**
     * Runs {@code source -> stages -> sink}, writing the result to {@code output} and reporting
     * lifecycle/progress events into {@code progress}: one {@link ProgressEvent.RunStarted}, a
     * {@link ProgressEvent.StageStarted}/{@link ProgressEvent.StageCompleted} pair around the
     * source, each stage, and the sink, then a terminal {@link ProgressEvent.RunCompleted} on
     * success or {@link ProgressEvent.RunFailed} (the exception is still rethrown) on failure.
     *
     * @param source produces the initial corpus
     * @param stages the filters applied in order
     * @param sink writes the final corpus to {@code output}
     * @param output the single output file
     * @param progress receives the run's lifecycle and progress events
     * @throws IOException if any step fails
     */
    public void run(
            Source source, List<Stage> stages, Sink sink, Path output, ProgressSink progress)
            throws IOException {
        int total = stages.size() + 2;
        progress.emit(new ProgressEvent.RunStarted(total));
        Path work = Files.createTempDirectory("p4suta-pipeline-");
        log.info("pipeline work area: {}", work);
        try {
            int position = 0;

            progress.emit(new ProgressEvent.StageStarted(source.name(), position, total));
            Corpus corpus = source.open(stageDir(work, 0, source.name()));
            log.info("source: {} page(s) at {} dpi", corpus.pageCount(), corpus.dpi());
            progress.emit(new ProgressEvent.StageCompleted(source.name()));
            position++;

            int dirIndex = 1;
            for (Stage stage : stages) {
                progress.emit(new ProgressEvent.StageStarted(stage.name(), position, total));
                corpus = stage.apply(corpus, stageDir(work, dirIndex, stage.name()));
                log.info("stage {} ({}): {} page(s)", dirIndex, stage.name(), corpus.pageCount());
                progress.emit(new ProgressEvent.StageCompleted(stage.name()));
                position++;
                dirIndex++;
            }

            progress.emit(new ProgressEvent.StageStarted(sink.name(), position, total));
            sink.write(corpus, output);
            log.info("wrote {}", output);
            progress.emit(new ProgressEvent.StageCompleted(sink.name()));

            progress.emit(new ProgressEvent.RunCompleted());
        } catch (IOException | RuntimeException e) {
            // Emit the stable ErrorCategory kind token (e.g. OUTPUT_CONFLICT) front ends localize
            // from — not the Java class name — with the throwable message as the developer detail.
            progress.emit(
                    new ProgressEvent.RunFailed(ExceptionMapper.map(e).kind().name(), message(e)));
            throw e;
        } finally {
            deleteRecursively(work);
        }
    }

    private static String message(Throwable e) {
        @Nullable String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private static Path stageDir(Path work, int index, String name) throws IOException {
        return Files.createDirectories(
                work.resolve(String.format(Locale.ROOT, "%02d-%s", index, name)));
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(PipelineRunner::deleteQuietly);
        } catch (IOException e) {
            log.warn("could not clean up work area {}: {}", dir, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("could not delete {}: {}", path, e.getMessage());
        }
    }
}
