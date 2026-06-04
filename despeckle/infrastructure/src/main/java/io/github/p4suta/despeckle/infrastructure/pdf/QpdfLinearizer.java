package io.github.p4suta.despeckle.infrastructure.pdf;

import io.github.p4suta.despeckle.port.PdfLinearizer;
import io.github.p4suta.shared.pdf.QpdfRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finishes an assembled PDF with a {@code qpdf --linearize} pass for Fast Web View, matching the
 * Python {@code pdfmeta} path. A thin app wrapper over the shared {@link QpdfRunner} capability
 * that keeps despeckle's DEGRADE-on-failure policy: this is a cosmetic container pass, so if qpdf
 * is missing or fails the (valid, non-linearized) PDF is kept and a warning logged, and neither the
 * pipeline nor topdf depends on qpdf being present.
 *
 * <p>The shared runner bakes in despeckle's qpdf facts — the default two-minute timeout and
 * accepting exit {@code 3} ("succeeded with warnings", which PDFBox's container routinely trips) —
 * via {@link QpdfRunner#linearizeOnly(String)}; the failure POLICY stays here. The sibling {@code
 * .linearized} temp + {@code Files.move} is kept on this side (the runner leaves it to the caller)
 * to avoid qpdf's {@code --replace-input} backup litter.
 */
public final class QpdfLinearizer implements PdfLinearizer {

    private static final Logger LOG = LoggerFactory.getLogger(QpdfLinearizer.class);

    // A plain --linearize runner (no --min-version / --newline-before-endstream, default 2-min
    // timeout, exit-3 accepted) resolving qpdf via despeckle's own -Ddespeckle.qpdf.path override.
    private final QpdfRunner runner = QpdfRunner.linearizeOnly("despeckle.qpdf.path");

    /** Creates a linearizer that shells out to the {@code qpdf} tool (best effort). */
    public QpdfLinearizer() {}

    /** Linearize {@code pdf} in place (best effort). */
    @Override
    public void linearize(Path pdf) {
        Path tmp = Path.of(pdf + ".linearized");
        try {
            // Write to a sibling temp, then move over the original — keeping qpdf's
            // --replace-input backup file (the *.~qpdf-orig) out of the output directory.
            runner.linearize(pdf, tmp);
            Files.move(tmp, pdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | TimeoutException e) {
            // The linearize pass is cosmetic: a missing qpdf or a hard error / unacceptable exit
            // (both surfaced by the shared runner as an IOException) and a timeout
            // (TimeoutException) must NOT fail the pipeline — keep the valid, un-linearized PDF and
            // warn.
            deleteQuietly(tmp);
            LOG.warn(
                    "could not linearize {} ({}); kept the un-linearized PDF", pdf, e.getMessage());
        } catch (InterruptedException e) {
            // Restore the interrupt flag the shared runner cleared, then degrade the same way: the
            // cosmetic linearize never fails the pipeline.
            Thread.currentThread().interrupt();
            deleteQuietly(tmp);
            LOG.warn(
                    "could not linearize {} ({}); kept the un-linearized PDF", pdf, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("could not delete {}: {}", path, e.getMessage());
        }
    }
}
