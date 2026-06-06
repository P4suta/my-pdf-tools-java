package io.github.p4suta.webapp.port;

import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.webapp.domain.ConversionRequest;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs one pdfbook conversion: reads {@code inputPdf}, writes the composed book to {@code
 * outputPdf}, and reports lifecycle/progress events into {@code progress}. The production adapter
 * shells out to the packaged pdfbook binary (process isolation), so a native crash cannot take down
 * the server.
 */
public interface ConversionEngine {

    /**
     * Converts {@code inputPdf} into {@code outputPdf} per {@code request}.
     *
     * @param request the conversion options
     * @param inputPdf the uploaded scan PDF to read
     * @param outputPdf the book PDF to write
     * @param progress receives the run's lifecycle and progress events
     * @throws IOException if the conversion fails to start or complete
     */
    void convert(ConversionRequest request, Path inputPdf, Path outputPdf, ProgressSink progress)
            throws IOException;
}
