package io.github.p4suta.despeckle.port;

import io.github.p4suta.despeckle.domain.model.ProcessResult;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A per-run report sink: it records each cleaned page's before/overlay/after panels and, at {@link
 * #finish()}, rolls them into the corpus artifacts. The concrete implementation ({@code
 * infrastructure.report.HtmlReporter}) is stateful (it probes {@code cwebp} and creates panel
 * directories), so it is built through {@link ReporterFactory} rather than constructed directly.
 *
 * <p>Both {@code inputImage} and {@code outputImage} are file paths on disk; no Leptonica {@code
 * Pix} or AWT image crosses this boundary, keeping the adapter's image stack confined to {@code
 * :infrastructure}.
 */
public interface Reporter extends AutoCloseable {

    /**
     * Render and record the panels for one page.
     *
     * @param relativeStem page path relative to the input root
     */
    void addPage(Path relativeStem, Path inputImage, Path outputImage, ProcessResult result)
            throws IOException;

    /** Write the corpus artifacts and index tying the run's pages together. */
    void finish() throws IOException;

    /** Closing a reporter finishes it, so it can be used as a try-with-resources. */
    @Override
    default void close() throws IOException {
        finish();
    }

    /**
     * A pass-through reporter that records nothing and writes nothing — the {@code null}-object the
     * application uses when reporting is disabled.
     */
    static Reporter noOp() {
        return new Reporter() {
            @Override
            public void addPage(
                    Path relativeStem, Path inputImage, Path outputImage, ProcessResult result) {}

            @Override
            public void finish() {}
        };
    }
}
