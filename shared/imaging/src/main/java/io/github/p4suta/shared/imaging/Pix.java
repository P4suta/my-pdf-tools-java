package io.github.p4suta.shared.imaging;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * An owning, {@link AutoCloseable} handle to a Leptonica {@code PIX}.
 *
 * <p>{@link #close()} (which calls {@code pixDestroy}) is the single release path. Use it with
 * try-with-resources; a {@code Pix} must not outlive its {@code close()}.
 *
 * <p>Exposes only primitive pixel operations: read / write / metadata, projection profiles,
 * geometry (clip / blank canvas / blit), orthogonal and arbitrary rotation, the raw skew estimate,
 * scaling, the raw size-select, and the boolean / morphological / counting ops. Policy that
 * composes these — confidence-gated deskew, the despeckle keep-condition — lives app-side.
 *
 * <p>Instances are not thread-safe, but independent {@code Pix} values on different threads are:
 * Leptonica's per-{@code PIX} operations are reentrant and the only process-global state (message
 * severity) is set once at load.
 */
public final class Pix implements AutoCloseable {

    // Null only after close(); requireHandle() turns a use-after-close into a clear exception and
    // gives NullAway a checked non-null value.
    private @Nullable MemorySegment handle;

    private Pix(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Read an image file into a new {@code Pix}. Throws a plain {@link IllegalStateException} on an
     * unreadable file (this module has no app domain exception model), which a per-app adapter can
     * translate into its own error kind.
     */
    public static Pix read(Path path) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(path.toString());
            MemorySegment raw = Leptonica.pixRead(name);
            if (raw.address() == 0) {
                throw new IllegalStateException("Leptonica could not read image: " + path);
            }
            return new Pix(raw);
        }
    }

    /** A fresh all-white 1 bpp canvas of the given size. */
    public static Pix blankCanvas(int width, int height) {
        return wrap(Leptonica.pixCreate(width, height, 1), "pixCreate");
    }

    private static Pix wrap(MemorySegment raw, String what) {
        if (raw.address() == 0) {
            throw new IllegalStateException("Leptonica returned NULL from " + what);
        }
        return new Pix(raw);
    }

    /**
     * Write this image to {@code path} using the given Leptonica {@code IFF_*} format. Internal;
     * the public API is the named writers ({@link #writeWebp}, {@link #writePng}, {@link
     * #writeTiffG4}, {@link #writePbm}, {@link #writeSameAs}), so no raw {@code IFF_*} code is
     * named outside this package.
     */
    void write(Path path, int format) {
        MemorySegment h = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(path.toString());
            int rc = Leptonica.pixWrite(name, h, format);
            if (rc != 0) {
                throw new IllegalStateException(
                        "Leptonica pixWrite failed (rc=" + rc + "): " + path);
            }
        }
    }

    /** Write this image as PNG. */
    public void writePng(Path path) {
        write(path, Leptonica.IFF_PNG);
    }

    /** Write this image as CCITT Group-4 TIFF (lossless for 1 bpp bitonal). */
    public void writeTiffG4(Path path) {
        write(path, Leptonica.IFF_TIFF_G4);
    }

    /** Write this image as binary PBM (P4) — 1 bpp. */
    public void writePbm(Path path) {
        write(path, Leptonica.IFF_PNM);
    }

    /** libwebp lossless effort (0–100, higher = smaller/slower); honored only with lossless=1. */
    private static final int WEBP_LOSSLESS_EFFORT = 75;

    /** Write this image as lossless WebP. */
    public void writeWebp(Path path) {
        MemorySegment h = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(path.toString());
            int rc = Leptonica.pixWriteWebP(name, h, WEBP_LOSSLESS_EFFORT, 1);
            if (rc != 0) {
                throw new IllegalStateException(
                        "Leptonica pixWriteWebP failed (rc=" + rc + "): " + path);
            }
        }
    }

    /**
     * Write in the format the source page was read from. The format is passed explicitly because a
     * derived {@link Pix} (created by Leptonica) reports {@link #inputFormat()} {@code == 0}.
     *
     * @param sourceFormat the original {@code IFF_*} format to reproduce
     */
    public void writeSameAs(Path path, int sourceFormat) {
        if (sourceFormat == Leptonica.IFF_WEBP) {
            writeWebp(path);
        } else {
            write(path, sourceFormat);
        }
    }

    public int width() {
        return Leptonica.pixGetWidth(requireHandle());
    }

    public int height() {
        return Leptonica.pixGetHeight(requireHandle());
    }

    /** The {@code IFF_*} format this image was read from. */
    public int inputFormat() {
        return Leptonica.pixGetInputFormat(requireHandle());
    }

    /**
     * The horizontal resolution in DPI recorded in the source image, or {@code 0} if it carried
     * none (PBM never does; a TIFF or PNG may).
     */
    public int resolution() {
        return Leptonica.pixGetXRes(requireHandle());
    }

    /**
     * Set this image's resolution (both axes) in DPI, so a format that records it (TIFF, PNG)
     * writes an accurate tag. No effect on pixel data; formats with no resolution field (PBM)
     * ignore it.
     */
    public void setResolution(int dpi) {
        Leptonica.pixSetResolution(requireHandle(), dpi, dpi);
    }

    /** Per-row foreground-pixel counts (length = {@link #height()}); the vertical ink profile. */
    public int[] inkByRow() {
        MemorySegment numa = Leptonica.pixCountPixelsByRow(requireHandle());
        return drainAndDestroyNuma(numa);
    }

    /**
     * Per-column foreground-pixel counts (length = {@link #width()}); the horizontal ink profile.
     */
    public int[] inkByColumn() {
        MemorySegment numa = Leptonica.pixCountPixelsByColumn(requireHandle());
        return drainAndDestroyNuma(numa);
    }

    private static int[] drainAndDestroyNuma(MemorySegment numa) {
        if (numa.address() == 0) {
            throw new IllegalStateException(
                    "Leptonica returned NULL counting pixels (is the input 1 bpp bitonal?)");
        }
        try (Arena arena = Arena.ofConfined()) {
            int n = Leptonica.numaGetCount(numa);
            int[] out = new int[n];
            MemorySegment slot = arena.allocate(JAVA_INT);
            for (int i = 0; i < n; i++) {
                Leptonica.numaGetIValue(numa, i, slot);
                out[i] = slot.get(JAVA_INT, 0);
            }
            return out;
        } finally {
            // The NUMA is a native allocation outside the Arena, so it must be freed on every
            // path — including an exception thrown from numaGetCount/numaGetIValue.
            destroyNuma(numa);
        }
    }

    private static void destroyNuma(MemorySegment numa) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pnuma = arena.allocate(ADDRESS);
            pnuma.set(ADDRESS, 0, numa);
            Leptonica.numaDestroy(pnuma);
        }
    }

    /** Rotate by {@code quads} 90-degree turns clockwise into a fresh {@code Pix}. */
    public Pix rotateOrth(int quads) {
        return wrap(Leptonica.pixRotateOrth(requireHandle(), quads), "pixRotateOrth");
    }

    /**
     * The raw skew estimate from Leptonica's row-projection finder: angle in degrees, confidence
     * ratio, and whether an estimate was made. Applies no policy (no confidence gate, no angle
     * clamp).
     */
    public SkewEstimate findSkew() {
        MemorySegment h = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pangle = arena.allocate(JAVA_FLOAT);
            MemorySegment pconf = arena.allocate(JAVA_FLOAT);
            int rc = Leptonica.pixFindSkew(h, pangle, pconf);
            return new SkewEstimate(pangle.get(JAVA_FLOAT, 0), pconf.get(JAVA_FLOAT, 0), rc == 0);
        }
    }

    /**
     * A raw skew estimate.
     *
     * @param angleDeg the estimated skew angle in degrees
     * @param conf the confidence ratio Leptonica assigns the estimate
     * @param found whether an estimate was produced ({@code pixFindSkew} returned 0)
     */
    public record SkewEstimate(double angleDeg, double conf, boolean found) {}

    /**
     * Rotate by {@code angleRadians} (positive clockwise), keeping 1 bpp, filling exposed white.
     */
    public Pix rotate(double angleRadians) {
        return wrap(
                Leptonica.pixRotate(
                        requireHandle(),
                        (float) angleRadians,
                        Leptonica.L_ROTATE_SAMPLING,
                        Leptonica.L_BRING_IN_WHITE,
                        0,
                        0),
                "pixRotate");
    }

    /**
     * Return a new {@code Pix} scaled to {@code targetWidth} x {@code targetHeight} px; a {@code 0}
     * dimension preserves the aspect ratio from the other.
     */
    public Pix scaleToSize(int targetWidth, int targetHeight) {
        return wrap(
                Leptonica.pixScaleToSize(requireHandle(), targetWidth, targetHeight),
                "pixScaleToSize");
    }

    /** Return a new {@code Pix} scaled to {@code targetHeight} px tall, preserving aspect ratio. */
    public Pix scaleToHeight(int targetHeight) {
        return scaleToSize(0, targetHeight);
    }

    /**
     * Return a new {@code Pix} cropped to the rectangle at {@code (x, y)} of size {@code w x h}
     * (clipped to this image's bounds).
     */
    public Pix clip(int x, int y, int w, int h) {
        MemorySegment src = requireHandle();
        MemorySegment box = Leptonica.boxCreate(x, y, w, h);
        try {
            return wrap(Leptonica.pixClipRectangle(src, box), "pixClipRectangle");
        } finally {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pbox = arena.allocate(ADDRESS);
                pbox.set(ADDRESS, 0, box);
                Leptonica.boxDestroy(pbox);
            }
        }
    }

    /**
     * Paint {@code src} onto this image with its top-left corner at {@code (dx, dy)}, copying
     * source pixels over the destination. Leptonica clips the blit to this image's bounds, so
     * {@code (dx, dy)} may be negative or push {@code src} partly off the edge.
     */
    public void blit(Pix src, int dx, int dy) {
        int rc =
                Leptonica.pixRasterop(
                        requireHandle(),
                        dx,
                        dy,
                        src.width(),
                        src.height(),
                        Leptonica.PIX_SRC,
                        src.requireHandle(),
                        0,
                        0);
        if (rc != 0) {
            throw new IllegalStateException("Leptonica pixRasterop (blit) failed (rc=" + rc + ")");
        }
    }

    /** Number of 8-connected foreground (black) components. */
    public int connectedComponents() {
        MemorySegment h = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = arena.allocate(JAVA_INT);
            Leptonica.pixCountConnComp(h, Leptonica.CONN_8, count);
            return count.get(JAVA_INT, 0);
        }
    }

    /** Number of foreground (black) pixels set in the image. */
    public long blackPixels() {
        MemorySegment h = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = arena.allocate(JAVA_INT);
            // The shared popcount table: without it Leptonica rebuilds (and frees) one per call.
            Leptonica.pixCountPixels(h, count, Leptonica.pixelSumTab8());
            return Integer.toUnsignedLong(count.get(JAVA_INT, 0));
        }
    }

    /**
     * Return a new {@code Pix} of the components that satisfy the size constraint: select on {@code
     * type} ({@code L_SELECT_WIDTH} / {@code L_SELECT_HEIGHT} / {@code L_SELECT_IF_EITHER} / {@code
     * L_SELECT_IF_BOTH}) using {@code relation} (e.g. {@code L_SELECT_IF_GT}) against the {@code w
     * x h} thresholds.
     *
     * @param connectivity the component connectivity (e.g. {@code 8})
     * @param type the size dimension to test ({@code L_SELECT_*})
     * @param relation the comparison relation ({@code L_SELECT_IF_*})
     */
    public Pix selectBySize(int w, int h, int connectivity, int type, int relation) {
        MemorySegment src = requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment changed = arena.allocate(JAVA_INT);
            MemorySegment raw =
                    Leptonica.pixSelectBySize(src, w, h, connectivity, type, relation, changed);
            return wrap(raw, "pixSelectBySize");
        }
    }

    /** Return a new {@code Pix} that is the bitwise inverse of this one. */
    public Pix inverted() {
        return wrap(Leptonica.pixInvert(requireHandle()), "pixInvert");
    }

    /**
     * Return a new {@code Pix} of this image's foreground minus {@code other}'s ({@code AND NOT}).
     */
    public Pix subtract(Pix other) {
        return wrap(Leptonica.pixSubtract(requireHandle(), other.requireHandle()), "pixSubtract");
    }

    /**
     * Return a new {@code Pix} grown by {@code radius} pixels in every direction (dilation by a
     * {@code (2*radius+1)} square). A {@code radius} of 0 is the identity.
     *
     * <p>Runs on Leptonica's word-accelerated DWA kernels for every size: bricks up to {@link
     * Leptonica#DWA_SAFE_BRICK} directly, larger ones as a chain of safe-size passes — exact,
     * because dilating by {@code brick(a)} then {@code brick(b)} equals dilating by {@code
     * brick(a+b-1)} (Minkowski sum; within the image rectangle the L∞ paths between in-bounds
     * points stay in bounds, so per-pass clipping changes nothing). Pixel-identity against the
     * generic rasterop path is pinned by {@code PixTest}'s full sweep.
     */
    public Pix dilated(int radius) {
        int size = 2 * radius + 1;
        if (size <= Leptonica.DWA_SAFE_BRICK) {
            return wrap(
                    Leptonica.pixDilateBrickDwa(requireHandle(), size, size), "pixDilateBrickDwa");
        }
        int covered = Leptonica.DWA_SAFE_BRICK;
        Pix current =
                wrap(
                        Leptonica.pixDilateBrickDwa(requireHandle(), covered, covered),
                        "pixDilateBrickDwa");
        while (covered < size) {
            // size and covered stay odd, so the step is odd and within the safe sel set.
            int step = Math.min(Leptonica.DWA_SAFE_BRICK, size - covered + 1);
            Pix next =
                    wrap(
                            Leptonica.pixDilateBrickDwa(current.requireHandle(), step, step),
                            "pixDilateBrickDwa");
            current.close();
            current = next;
            covered += step - 1;
        }
        return current;
    }

    /** The generic rasterop dilation — kept as the DWA equality oracle for {@code PixTest}. */
    Pix dilatedGeneric(int radius) {
        int size = 2 * radius + 1;
        return wrap(Leptonica.pixDilateBrick(requireHandle(), size, size), "pixDilateBrick");
    }

    /**
     * Return a new {@code Pix} opened (eroded then dilated) by a {@code (2*radius+1)} square — i.e.
     * foreground thinner than the brick in either axis is erased, leaving only the solid parts.
     *
     * <p>Bricks up to {@link Leptonica#DWA_SAFE_BRICK} run on Leptonica's word-accelerated DWA
     * kernels — pixel-identical to the generic rasterop path (pinned by {@code PixTest}'s sweep)
     * and several times faster; larger bricks fall back to the generic path (an opening, unlike a
     * dilation, does not compose from smaller passes).
     */
    public Pix opened(int radius) {
        int size = 2 * radius + 1;
        if (size <= Leptonica.DWA_SAFE_BRICK) {
            return wrap(Leptonica.pixOpenBrickDwa(requireHandle(), size, size), "pixOpenBrickDwa");
        }
        return openedGeneric(radius);
    }

    /** The generic rasterop opening — the large-brick fallback and the DWA equality oracle. */
    Pix openedGeneric(int radius) {
        int size = 2 * radius + 1;
        return wrap(Leptonica.pixOpenBrick(requireHandle(), size, size), "pixOpenBrick");
    }

    /**
     * Return a new {@code Pix} of the intersection of this image's foreground with {@code other}'s.
     */
    public Pix and(Pix other) {
        return wrap(Leptonica.pixAnd(requireHandle(), other.requireHandle()), "pixAnd");
    }

    /** Return a new {@code Pix} of the union of this image's foreground with {@code other}'s. */
    public Pix or(Pix other) {
        return wrap(Leptonica.pixOr(requireHandle(), other.requireHandle()), "pixOr");
    }

    /** Whether {@code other} is pixel-identical to this image. */
    public boolean pixelsEqual(Pix other) {
        MemorySegment h = requireHandle();
        MemorySegment otherHandle = other.requireHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment same = arena.allocate(JAVA_INT);
            Leptonica.pixEqual(h, otherHandle, same);
            return same.get(JAVA_INT, 0) == 1;
        }
    }

    @Override
    public void close() {
        MemorySegment h = handle;
        if (h == null) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            // pixDestroy takes a PIX **: a slot holding the pointer, nulled on return.
            MemorySegment slot = arena.allocate(ADDRESS);
            slot.set(ADDRESS, 0, h);
            Leptonica.pixDestroy(slot);
        } finally {
            handle = null;
        }
    }

    private MemorySegment requireHandle() {
        MemorySegment h = handle;
        if (h == null) {
            throw new IllegalStateException("Pix has already been closed");
        }
        return h;
    }
}
