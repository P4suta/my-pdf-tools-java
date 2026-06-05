package io.github.p4suta.register.infrastructure.registrar;

import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Canvas;
import io.github.p4suta.register.domain.model.Detection;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PageAnalysis;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.domain.model.Skew;
import io.github.p4suta.register.domain.model.Transform;
import io.github.p4suta.register.domain.service.Reference;
import io.github.p4suta.register.domain.service.TransformPlanner;
import io.github.p4suta.register.port.PageRegistrar;
import io.github.p4suta.shared.imaging.Pix;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The Leptonica-backed {@link PageRegistrar}: the per-page registration pipeline
 *
 * <pre>
 *   (deskew) → detect main column → scale to reference height → translate to anchor → composite
 * </pre>
 *
 * <p>The one adapter that drives {@link Pix}. Stateless apart from its (stateless) detector and
 * planner helpers, so it is safe to share across the corpus worker pool. {@link #analyze} deskews
 * and detects once, writing the deskewed page to a scratch file; {@link #renderPlaced} reads that
 * back and places it with the detection {@code analyze} already found, so the costly deskew and
 * detection are never repeated per page. {@code renderPlaced} returns the page's {@link
 * PageDiagnostic}; the caller forwards it to a {@code Reporter} only on a diagnostics run.
 */
public final class LeptonicaPageRegistrar implements PageRegistrar {

    private final MainColumnDetector detector = new MainColumnDetector();
    private final TransformPlanner planner = new TransformPlanner();

    @Override
    public int readScanResolution(Path file) {
        try (Pix page = Pix.read(file)) {
            return page.resolution();
        }
    }

    @Override
    public PageAnalysis analyze(
            Path source, Path deskewedScratch, RegisterOptions options, boolean recordSkew) {
        try (Pix page = Pix.read(source)) {
            // Capture the format from the ORIGINAL page: deskew produces a fresh PIX that has lost
            // it
            // (SAME -> unknown), and so does the bitonal scratch we write below.
            int sourceFormat = page.inputFormat();
            int dpi = effectiveDpi(page.resolution(), options);
            if (!options.deskew()) {
                // No deskew runs here, so only a standalone measurement is wanted for diagnostics.
                @Nullable Skew skew = recordSkew ? toSkew(Deskewer.measureSkew(page)) : null;
                page.writeTiffG4(deskewedScratch);
                return new PageAnalysis(
                        page.width(),
                        page.height(),
                        detector.detect(page, dpi),
                        skew,
                        sourceFormat);
            }
            // One combined measure-and-deskew: the SAME estimate that gated the straightening is
            // reused for the diagnostic Skew, so the deskew path runs exactly one findSkew per
            // page.
            Deskewer.DeskewResult result = Deskewer.deskewWithEstimate(page);
            try (Pix deskewed = result.page()) {
                @Nullable Skew skew = recordSkew ? toSkew(result.estimate()) : null;
                deskewed.writeTiffG4(deskewedScratch);
                return new PageAnalysis(
                        deskewed.width(),
                        deskewed.height(),
                        detector.detect(deskewed, dpi),
                        skew,
                        sourceFormat);
            }
        }
    }

    @Override
    public PageDiagnostic renderPlaced(
            Path deskewedScratch,
            PageAnalysis analysis,
            int index,
            Parity parity,
            String source,
            @Nullable Reference reference,
            Canvas canvas,
            Path dest,
            OutputFormat format,
            RegisterOptions options) {
        try (Pix deskewed = Pix.read(deskewedScratch)) {
            Optional<Detection> detection = analysis.detection();
            PageDiagnostic.Placement placement;
            try (Pix canvasPix = Pix.blankCanvas(canvas.width(), canvas.height())) {
                if (detection.isPresent() && reference != null) {
                    placement =
                            place(
                                    deskewed,
                                    detection.get().column(),
                                    parity,
                                    reference,
                                    canvas,
                                    canvasPix,
                                    options);
                } else {
                    // No detectable column (blank/garbled page) or no corpus reference: center
                    // as-is.
                    placement = centerPlace(deskewed, canvas, canvasPix);
                }
                canvasPix.setResolution(options.canvasDpi());
                writeIn(canvasPix, dest, format, analysis.sourceFormat());
            }
            return buildDiagnostic(
                    index,
                    parity,
                    source,
                    deskewed,
                    canvas,
                    detection,
                    reference,
                    placement,
                    analysis.skew(),
                    options.deskew());
        }
    }

    /**
     * Write {@code pix} in the requested {@link OutputFormat} via {@link Pix}'s named writers, so
     * no Leptonica {@code IFF_*} code is named app-side. {@code SAME} threads the page's source
     * format (a derived Pix reports {@code inputFormat()==0}, so it is passed explicitly).
     *
     * @param pix the placed canvas
     * @param output the destination path
     * @param format the desired output format
     * @param sourceFormat the {@code IFF_*} the page was read from (for {@link OutputFormat#SAME})
     */
    private static void writeIn(Pix pix, Path output, OutputFormat format, int sourceFormat) {
        switch (format) {
            case SAME -> pix.writeSameAs(output, sourceFormat);
            case PBM -> pix.writePbm(output);
            case PNG -> pix.writePng(output);
            case TIFF -> pix.writeTiffG4(output);
            case WEBP -> pix.writeWebp(output);
        }
    }

    private PageDiagnostic.Placement place(
            Pix work,
            Box column,
            Parity parity,
            Reference reference,
            Canvas canvas,
            Pix canvasPix,
            RegisterOptions options) {
        Transform transform =
                planner.plan(
                        column,
                        reference.forParity(parity),
                        work.width(),
                        work.height(),
                        canvas.width(),
                        canvas.height(),
                        options.scale(),
                        options.outlierRatio(),
                        options.anchor());
        // A reference-anchored page is placed exactly at its target and the scan margin that
        // overflows is cropped, so the text block lands identically on every page. An outlier (no
        // reliable column) is instead clamped to stay whole: we don't know where its text is, so
        // cropping could lose content. CENTER is a per-page cosmetic mode that always stays whole.
        boolean crop = options.anchor() == Anchor.TOP_RIGHT && !transform.passthrough();
        if (transform.scale() == 1.0) {
            return blitPlaced(canvasPix, work, transform, canvas, true, crop);
        }
        int targetHeight = Math.max(1, (int) Math.round(work.height() * transform.scale()));
        try (Pix scaled = work.scaleToHeight(targetHeight)) {
            return blitPlaced(canvasPix, scaled, transform, canvas, true, crop);
        }
    }

    private static PageDiagnostic.Placement centerPlace(Pix work, Canvas canvas, Pix canvasPix) {
        int dx = center(canvas.width(), work.width());
        int dy = center(canvas.height(), work.height());
        Transform centered = new Transform(false, 1.0, dx, dy);
        return blitPlaced(canvasPix, work, centered, canvas, false, false);
    }

    /**
     * Blit {@code content} onto the canvas and report where it landed. When {@code crop}, it is
     * placed exactly at the transform's offset and Leptonica crops whatever overflows the canvas
     * edges (how a reference-anchored page reaches a fixed text-block position). Otherwise the
     * offset is clamped so the whole image stays on the canvas — nothing is cropped.
     */
    private static PageDiagnostic.Placement blitPlaced(
            Pix canvasPix,
            Pix content,
            Transform transform,
            Canvas canvas,
            boolean detected,
            boolean crop) {
        int placedX =
                crop
                        ? transform.dx()
                        : clampPlacement(transform.dx(), content.width(), canvas.width());
        int placedY =
                crop
                        ? transform.dy()
                        : clampPlacement(transform.dy(), content.height(), canvas.height());
        canvasPix.blit(content, placedX, placedY);
        boolean croppedX = placedX < 0 || placedX + content.width() > canvas.width();
        boolean croppedY = placedY < 0 || placedY + content.height() > canvas.height();
        return new PageDiagnostic.Placement(
                detected,
                transform.passthrough(),
                transform.scale(),
                transform.dx(),
                transform.dy(),
                placedX,
                placedY,
                croppedX,
                croppedY,
                content.width(),
                content.height());
    }

    private static PageDiagnostic buildDiagnostic(
            int index,
            Parity parity,
            String source,
            Pix work,
            Canvas canvas,
            Optional<Detection> detection,
            @Nullable Reference reference,
            PageDiagnostic.Placement placement,
            @Nullable Skew skew,
            boolean deskewEnabled) {
        PageDiagnostic.@Nullable Column column =
                detection
                        .map(
                                det ->
                                        new PageDiagnostic.Column(
                                                det.column(),
                                                det.verticalBand().start(),
                                                det.verticalBand().endExclusive()))
                        .orElse(null);
        Box referenceBox = reference == null ? null : reference.forParity(parity);
        return new PageDiagnostic(
                index,
                parity,
                source,
                work.width(),
                work.height(),
                canvas.width(),
                canvas.height(),
                deskewEnabled,
                skew,
                column,
                referenceBox,
                placement);
    }

    /** Convert a register skew estimate into the domain {@link Skew} value type. */
    private static Skew toSkew(Deskewer.SkewEstimate est) {
        return new Skew(est.angleDeg(), est.conf(), est.found(), est.correctable());
    }

    /**
     * Offset to place a {@code contentExtent}-long run inside {@code canvasExtent} without
     * clipping: the requested {@code offset} clamped into {@code [0, canvasExtent -
     * contentExtent]}. If the content is larger than the canvas, the unavoidable overflow is split
     * evenly.
     */
    private static int clampPlacement(int offset, int contentExtent, int canvasExtent) {
        int max = canvasExtent - contentExtent;
        if (max < 0) {
            return max / 2;
        }
        return Math.max(0, Math.min(max, offset));
    }

    private static int effectiveDpi(int pageResolution, RegisterOptions options) {
        // The gutter heuristic works in page pixels, so it needs the scan's own DPI. An explicit
        // --dpi is authoritative: the pipeline passes the true scan DPI this way so it never has to
        // re-tag every extracted page just for this. Otherwise use the page's embedded resolution,
        // falling back to the canvas DPI for a format that carries none (PBM reports 0).
        if (options.dpi().isPresent()) {
            return options.dpi().getAsInt();
        }
        return pageResolution > 0 ? pageResolution : options.canvasDpi();
    }

    private static int center(int canvasExtent, int contentExtent) {
        return (canvasExtent - contentExtent) / 2;
    }
}
