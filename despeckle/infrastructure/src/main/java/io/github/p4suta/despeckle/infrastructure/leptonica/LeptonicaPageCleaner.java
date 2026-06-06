package io.github.p4suta.despeckle.infrastructure.leptonica;

import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.domain.model.ProcessResult;
import io.github.p4suta.despeckle.port.PageCleaner;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.kernel.Resolution;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The despeckle pipeline for a single page:
 *
 * <pre>
 *   read → keep components larger than k → (optionally) fill holes → write
 * </pre>
 *
 * <p>Connected-component analysis is delegated to Leptonica's {@code pixSelectBySize}; this class
 * sequences the calls and accounts for what changed. Stateless, so safe to share across threads.
 *
 * <p>The Leptonica adapter side of {@link PageCleaner}: it owns the {@link Pix} handles and
 * resolves an {@link OutputFormat} to the Leptonica {@code IFF_*} write code, neither of which
 * crosses the port boundary. The shared imaging island exposes only the raw {@code selectBySize}
 * primitive; despeckle's keep-larger-than policy (the {@code IF_EITHER}/{@code IF_GT}
 * keep-condition) lives here, app-side, in {@link #keepComponentsLargerThan(Pix, int)}.
 */
public final class LeptonicaPageCleaner implements PageCleaner {

    // Leptonica size-selection flags, literal pix.h header values. The shared imaging island holds
    // them as package-private constants and exposes only the raw selectBySize(...) primitive, so
    // they cannot be named cross-package; the keep-larger-than policy that composes them is
    // app-side, so the values it needs live here.

    /** 8-connectivity for connected-component analysis. */
    private static final int CONN_8 = 8;

    /** Constraint satisfied if either dimension matches. */
    private static final int L_SELECT_IF_EITHER = 5;

    /** Relation: keep if value is greater than the threshold. */
    private static final int L_SELECT_IF_GT = 2;

    /** Process one page from {@code input} to {@code output}. */
    @Override
    public ProcessResult clean(
            Path input, Path output, OutputFormat format, ProcessOptions options) {
        try (Pix source = Pix.read(input)) {
            // pixGetXRes returns 0 when the page carries no embedded resolution. Map that
            // 0-means-absent to an Optional<Resolution> once here, so the domain models a missing
            // resolution with absence rather than a magic value.
            int raw = source.resolution();
            Optional<Resolution> img = raw > 0 ? Optional.of(Resolution.of(raw)) : Optional.empty();
            int k = options.speckSize(img);
            int componentsBefore = source.connectedComponents();
            long blackBefore = source.blackPixels();
            int sourceFormat = source.inputFormat();

            Pix current = keepComponentsLargerThan(source, k);
            try {
                if (options.isolatedDustEnabled()) {
                    Pix deisolated =
                            removeIsolatedDust(
                                    current,
                                    options.isolatedDustSize(img),
                                    options.isolatedDustProximity(img));
                    current.close();
                    current = deisolated;
                }

                if (options.fillHoles()) {
                    // Thin-stroke threshold: only holes ringed by black thicker than this count as
                    // pin-holes. Half the speck size (~3 px at 600 dpi) keeps body strokes solid
                    // while sparing the fine gaps inside small or complex glyphs.
                    int strokeThickness = Math.max(1, Math.round(k / 2.0f));
                    Pix filled = fillHoles(current, k, strokeThickness);
                    current.close();
                    current = filled;
                }

                int componentsAfter = current.connectedComponents();
                long blackAfter = current.blackPixels();
                // Stamp the honored resolution so a TIFF/PNG output carries an accurate tag. Only a
                // known resolution is written; an unknown one is left untouched.
                options.resolution(img).map(Resolution::dpi).ifPresent(current::setResolution);
                writeIn(current, output, format, sourceFormat);
                return new ProcessResult(
                        componentsBefore, componentsAfter, blackBefore, blackAfter);
            } finally {
                current.close();
            }
        }
    }

    /**
     * Return a new {@code Pix} keeping only components whose bounding box is larger than {@code k}
     * in <em>either</em> width or height (8-connected).
     *
     * <p>The keep-condition the shared imaging island does not carry (it exposes only the raw
     * {@code selectBySize} primitive). Scanner dust is a few pixels across in both dimensions, so
     * it fails the {@code > k} test on both and is dropped; punctuation, dakuten and ruby — and
     * even a thin vertical stroke, which is tall — clear it on at least one axis and survive. The
     * {@code (IF_EITHER, IF_GT)} polarity is the <em>keep</em> condition; the opposite polarity
     * erases.
     */
    private static Pix keepComponentsLargerThan(Pix pix, int k) {
        return pix.selectBySize(k, k, CONN_8, L_SELECT_IF_EITHER, L_SELECT_IF_GT);
    }

    /**
     * Write {@code pix} in the requested {@link OutputFormat} via {@link Pix}'s named writers, so
     * no Leptonica {@code IFF_*} code is named app-side. {@code SAME} threads {@code sourceFormat}
     * through explicitly because a derived Pix reports {@code inputFormat()==0}.
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

    /**
     * Fill white pin-holes inside black strokes, but only where the surrounding ink is solid. A
     * pin-hole is a small white defect ringed by thick black; the fine white gaps inside small or
     * complex glyphs look the same to a size filter but are ringed by <em>thin</em> strokes, so a
     * plain "fill every small hole" pass crushes them. Opening the page by {@code strokeThickness}
     * keeps only the solid ink; a hole is filled only when it still sits inside that solid mask.
     *
     * @param pix the page
     * @param k the speck size — caps a candidate hole at {@code k} px in either axis
     * @param strokeThickness ink thinner than this is not "solid", so its holes are left alone
     */
    private static Pix fillHoles(Pix pix, int k, int strokeThickness) {
        try (Pix holes = smallHoles(pix, k);
                Pix solid = solidInk(pix, k, strokeThickness);
                Pix fillable = holes.and(solid)) {
            return pix.or(fillable);
        }
    }

    /** The white holes of {@code pix} no larger than {@code k} px in either axis. */
    private static Pix smallHoles(Pix pix, int k) {
        try (Pix inverted = pix.inverted();
                Pix largerWhite = keepComponentsLargerThan(inverted, k)) {
            return inverted.subtract(largerWhite);
        }
    }

    /**
     * {@code pix} reduced to ink thicker than {@code strokeThickness}, with its pin-holes filled.
     */
    private static Pix solidInk(Pix pix, int k, int strokeThickness) {
        try (Pix thick = pix.opened(strokeThickness);
                Pix thickHoles = smallHoles(thick, k)) {
            return thick.or(thickHoles);
        }
    }

    /**
     * Remove specks that are both small enough to be dust (no larger than {@code maxSize} in either
     * axis) and isolated (no kept component within {@code proximity} pixels). Real text is large on
     * at least one axis, so it forms the protected set; punctuation, dakuten and ruby are small but
     * always hug a glyph, so they fall inside that set's neighborhood and are spared. Only specks
     * out on clean background are dropped.
     */
    private static Pix removeIsolatedDust(Pix base, int maxSize, int proximity) {
        try (Pix text = keepComponentsLargerThan(base, maxSize);
                Pix candidates = base.subtract(text);
                Pix textNeighborhood = text.dilated(proximity);
                Pix isolated = candidates.subtract(textNeighborhood)) {
            return base.subtract(isolated);
        }
    }
}
