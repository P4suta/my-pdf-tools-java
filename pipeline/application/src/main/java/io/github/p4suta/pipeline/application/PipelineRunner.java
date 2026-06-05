package io.github.p4suta.pipeline.application;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.pipeline.port.Stage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
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
     * Runs {@code source -> stages -> sink}, writing the result to {@code output}.
     *
     * @param source produces the initial corpus
     * @param stages the filters applied in order
     * @param sink writes the final corpus to {@code output}
     * @param output the single output file
     * @throws IOException if any step fails
     */
    public void run(Source source, List<Stage> stages, Sink sink, Path output) throws IOException {
        Path work = Files.createTempDirectory("p4suta-pipeline-");
        log.info("pipeline work area: {}", work);
        try {
            Corpus corpus = source.open(stageDir(work, 0, "source"));
            log.info("source: {} page(s) at {} dpi", corpus.pageCount(), corpus.dpi());

            int index = 1;
            for (Stage stage : stages) {
                corpus = stage.apply(corpus, stageDir(work, index, stage.name()));
                log.info("stage {} ({}): {} page(s)", index, stage.name(), corpus.pageCount());
                index++;
            }

            sink.write(corpus, output);
            log.info("wrote {}", output);
        } finally {
            deleteRecursively(work);
        }
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
