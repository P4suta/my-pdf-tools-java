package io.github.p4suta.register.port;

import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.RunInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A per-run diagnostics sink (the {@code --diag} mode): it records each page's {@link
 * PageDiagnostic} as the render pass produces it and, at {@link #finish}, rolls them into the
 * corpus artifacts (the JSONL log, the summary, the before/after corpus overlay, the residual chart
 * and an optional WebP flip-book). The concrete implementation ({@code
 * infrastructure.diag.Diagnostics}) is stateful (it creates the report directory and accumulates
 * the page diagnostics), so it is built through {@link ReporterFactory} rather than constructed
 * directly.
 *
 * <p>No Leptonica {@code Pix} or AWT image crosses this boundary: {@link #addPage} receives only
 * the domain diagnostic and the path of the deskewed page on disk, which the adapter re-reads to
 * draw the overlay — keeping the image stack confined to {@code :infrastructure}.
 */
public interface Reporter {

    /**
     * Record one rendered page: draw its overlay from the deskewed page on disk and remember its
     * diagnostic for the end-of-run report.
     *
     * @param diagnostic the page's recorded state
     * @param deskewedPage the deskewed page on disk (the scratch the render pass read back)
     * @throws IOException if the overlay cannot be written
     */
    void addPage(PageDiagnostic diagnostic, Path deskewedPage) throws IOException;

    /**
     * Write the corpus artifacts tying the run's pages together.
     *
     * @param info the run settings and the derived per-parity references
     * @param outputs the registered output image paths in reading order (flip-book frames)
     * @throws IOException if an artifact cannot be written
     */
    void finish(RunInfo info, List<Path> outputs) throws IOException;

    /**
     * A pass-through reporter that records nothing and writes nothing — the {@code null}-object the
     * application uses when diagnostics are disabled.
     *
     * @return a no-op reporter
     */
    static Reporter noOp() {
        return new Reporter() {
            @Override
            public void addPage(PageDiagnostic diagnostic, Path deskewedPage) {}

            @Override
            public void finish(RunInfo info, List<Path> outputs) {}
        };
    }
}
