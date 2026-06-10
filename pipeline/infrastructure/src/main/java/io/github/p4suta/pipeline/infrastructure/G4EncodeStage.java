package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.io.CorpusFiles;
import io.github.p4suta.shared.process.Tasks;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The G4-normalization {@link Stage}: re-encodes each extracted page as single-strip CCITT G4 TIFF
 * via Leptonica, which {@link SpreadPackSink}'s pass-through CCITT embedding requires. The
 * extractor's decoded mode ({@code pdfimages -tiff}, used for any source that is not all-CCITT)
 * writes poppler's default (non-G4) TIFF compression at a default 72 dpi, so that output cannot be
 * embedded directly; despeckle and register each re-encode their output as G4 themselves, so the
 * composition root inserts this stage only when neither of them runs. The corpus dpi is stamped on
 * every page. (For an all-CCITT source the extractor's remux already produces stamped single-strip
 * G4 — this stage then re-encodes losslessly, a small constant cost that keeps the no-stage path
 * uniform.)
 */
public final class G4EncodeStage implements Stage {

    /** Same lowercase {@code .tif} extension the extract source produces. */
    private static final String OUTPUT_GLOB = "*.tif";

    private final int jobs;
    private final ProgressSink progress;

    /** {@link ProgressSink#NO_OP} variant of the two-arg constructor. */
    public G4EncodeStage(int jobs) {
        this(jobs, ProgressSink.NO_OP);
    }

    /**
     * @param jobs worker threads per book
     * @param progress sink that each encoded page is reported into as a {@code PageProcessed} event
     */
    public G4EncodeStage(int jobs, ProgressSink progress) {
        this.jobs = jobs;
        this.progress = progress;
    }

    @Override
    public String name() {
        return "encode";
    }

    @Override
    public Corpus apply(Corpus input, Path workDir) throws IOException {
        List<Path> pages = CorpusFiles.collect(input.dir(), input.glob());
        List<Callable<Void>> tasks = new ArrayList<>(pages.size());
        for (Path src : pages) {
            tasks.add(
                    () -> {
                        Path out = CorpusFiles.mirrorDestination(src, input.dir(), workDir, "tif");
                        try (Pix page = Pix.read(src)) {
                            page.setResolution(input.dpi());
                            page.writeTiffG4(out);
                        }
                        return null;
                    });
        }
        // Platform workers: each task is a Leptonica decode + G4 encode (CPU-bound FFM downcalls,
        // which would pin virtual threads' carriers). Progress arrives on this thread, ordered.
        Tasks.awaitAll(
                Tasks.Workers.platform(jobs),
                tasks,
                "G4 encode",
                (done, total) ->
                        progress.emit(new ProgressEvent.PageProcessed(name(), done, total)));
        return input.movedTo(workDir, OUTPUT_GLOB);
    }
}
