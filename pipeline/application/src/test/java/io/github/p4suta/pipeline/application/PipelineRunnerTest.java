package io.github.p4suta.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.pipeline.port.Stage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineRunnerTest {

    @TempDir Path tmp;

    @Test
    void runsSourceThenStagesThenSinkInOrderAndCleansUp() throws IOException {
        List<String> order = new ArrayList<>();
        List<Path> sourceWorkDir = new ArrayList<>();

        Source source =
                workDir -> {
                    order.add("source");
                    sourceWorkDir.add(workDir);
                    Files.writeString(workDir.resolve("page-000.tif"), "img");
                    return new Corpus(workDir, "*.tif", 600, 1);
                };
        Stage despeckle = fakeStage("despeckle", order, "*.tif");
        Stage register = fakeStage("register", order, "*.tiff");

        Path output = tmp.resolve("out.pdf");
        Corpus[] sunk = new Corpus[1];
        Sink sink =
                (corpus, out) -> {
                    order.add("sink");
                    sunk[0] = corpus;
                    Files.writeString(out, "%PDF-1.7");
                };

        new PipelineRunner().run(source, List.of(despeckle, register), sink, output);

        assertThat(order).containsExactly("source", "despeckle", "register", "sink");
        assertThat(output).exists();
        // The sink receives register's output corpus: register's glob, the dpi/count threaded
        // through.
        assertThat(sunk[0].glob()).isEqualTo("*.tiff");
        assertThat(sunk[0].dpi()).isEqualTo(600);
        // Stages ran in their own numbered subdirectories under one work area, now removed.
        Path workArea = sourceWorkDir.get(0).getParent();
        assertThat(sourceWorkDir.get(0).getFileName()).hasToString("00-source");
        assertThat(workArea).doesNotExist();
    }

    @Test
    void runsWithNoStages() throws IOException {
        Source source =
                workDir -> {
                    Files.writeString(workDir.resolve("page-000.tif"), "img");
                    return new Corpus(workDir, "*.tif", 300, 1);
                };
        Path output = tmp.resolve("nostages.pdf");
        Sink sink = (corpus, out) -> Files.writeString(out, "%PDF-1.7");

        new PipelineRunner().run(source, List.of(), sink, output);

        assertThat(output).exists();
    }

    private static Stage fakeStage(String name, List<String> order, String outGlob) {
        return new Stage() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Corpus apply(Corpus input, Path workDir) throws IOException {
                order.add(name);
                Files.writeString(workDir.resolve("page-000" + outGlob.substring(1)), "img");
                return input.movedTo(workDir, outGlob);
            }
        };
    }
}
