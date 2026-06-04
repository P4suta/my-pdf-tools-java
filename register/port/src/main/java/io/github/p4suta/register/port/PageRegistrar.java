package io.github.p4suta.register.port;

import io.github.p4suta.register.domain.model.Canvas;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PageAnalysis;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.domain.service.Reference;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Registers one page at a time, the sole abstraction over the Leptonica pixel pipeline (deskew,
 * main-column detection, scale-to-reference and placement). The implementation ({@code
 * infrastructure.registrar.LeptonicaPageRegistrar}) keeps {@code Pix} and the FFM island entirely
 * on its side of this boundary: only file paths and the domain value types cross it.
 * Implementations are stateless and safe to share across the corpus worker pool.
 *
 * <p>A run drives this in two passes — analyze every page to a per-parity median reference, then
 * place each page against it — so {@link #analyze} writes the deskewed page to a scratch file that
 * {@link #renderPlaced} reads back, doing the costly deskew and detection once.
 */
public interface PageRegistrar {

    /**
     * The horizontal scan resolution recorded in {@code file}, or {@code 0} if it carries none.
     *
     * @param file the image to probe
     * @return the resolution in DPI, or 0
     */
    int readScanResolution(Path file);

    /**
     * Analyze one page: deskew (per {@code options}), detect the main column, and write the
     * deskewed image to {@code deskewedScratch} for {@link #renderPlaced} to read back.
     *
     * @param source the source page image
     * @param deskewedScratch where to write the deskewed page for the render pass
     * @param options the registration knobs (deskew flag, resolution)
     * @param recordSkew whether to also measure the skew for diagnostics (an extra pass; off on a
     *     normal run)
     * @return the page's Pix-free analysis (size, detection, optional skew, source-format token)
     */
    PageAnalysis analyze(
            Path source, Path deskewedScratch, RegisterOptions options, boolean recordSkew);

    /**
     * Register an already-deskewed page (read back from {@code deskewedScratch}) onto a fresh
     * canvas and write it to {@code dest}, reusing the analysis the analysis pass produced.
     *
     * @param deskewedScratch the deskewed page written by {@link #analyze}
     * @param analysis that page's analysis result
     * @param index the 0-based page index (for diagnostics)
     * @param parity which side of the spread the page is
     * @param source the source file name (for diagnostics labels)
     * @param reference the corpus reference, or null when none could be derived (every page
     *     centered)
     * @param canvas the fixed output canvas
     * @param dest the file to write the registered page to
     * @param format the output format
     * @param options the registration knobs (anchor, scale, outlier ratio, canvas DPI)
     * @return the page's recorded diagnostic state (its skew is null unless {@code analyze}
     *     recorded it); the caller passes it to a {@code Reporter} only on a diagnostics run
     */
    PageDiagnostic renderPlaced(
            Path deskewedScratch,
            PageAnalysis analysis,
            int index,
            Parity parity,
            String source,
            @Nullable Reference reference,
            Canvas canvas,
            Path dest,
            OutputFormat format,
            RegisterOptions options);
}
