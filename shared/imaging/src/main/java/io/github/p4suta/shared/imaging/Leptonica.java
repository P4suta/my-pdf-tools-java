package io.github.p4suta.shared.imaging;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Every Foreign Function &amp; Memory binding to the system Leptonica library, behind one {@link
 * MethodHandle} cache and one library loader. Other classes work in terms of {@link Pix} and never
 * touch a raw {@link MethodHandle} or {@link MemorySegment}.
 *
 * <p>The constants below are the literal values from the Leptonica 1.82.0 headers ({@code
 * imageio.h}, {@code environ.h}, {@code rasterop.h}, {@code rotate.h}, {@code pix.h}); re-confirm
 * them if the pinned Leptonica version changes.
 *
 * <p>The class-level {@code @SuppressWarnings("restricted")} scopes the FFM restricted-method
 * exemption ({@code System.load}, {@code Linker.downcallHandle}) here, so a restricted call
 * elsewhere still fails the {@code -Werror} build.
 */
@SuppressWarnings("restricted")
final class Leptonica {

    private Leptonica() {}

    // image file formats (imageio.h)
    /** PNG. */
    static final int IFF_PNG = 3;

    /** Uncompressed TIFF. */
    static final int IFF_TIFF = 4;

    /** CCITT Group-4 fax-compressed TIFF (1 bpp). */
    static final int IFF_TIFF_G4 = 8;

    /** Portable aNy Map (PBM/PGM/PPM); a 1 bpp image writes as binary P4. */
    static final int IFF_PNM = 11;

    /**
     * WebP. Use {@link #pixWriteWebP} (lossless); the generic {@code pixWrite} defaults to lossy.
     */
    static final int IFF_WEBP = 15;

    // size-selection flags (pix.h)
    /** Select on width. */
    static final int L_SELECT_WIDTH = 1;

    /** Select on height. */
    static final int L_SELECT_HEIGHT = 2;

    /** Constraint satisfied if either dimension matches. */
    static final int L_SELECT_IF_EITHER = 5;

    /** Constraint satisfied only if both dimensions match. */
    static final int L_SELECT_IF_BOTH = 6;

    /** Relation: keep if value is greater than the threshold. */
    static final int L_SELECT_IF_GT = 2;

    // message severity (environ.h)
    /** Highest severity: suppress all Leptonica diagnostics. */
    static final int L_SEVERITY_NONE = 6;

    // connectivity
    /** 8-connectivity (diagonal neighbors count) for connected-component analysis. */
    static final int CONN_8 = 8;

    // rasterop op codes (rasterop.h)
    /** Copy the source over the destination ({@code PIX_SRC == 0xc} in rasterop.h). */
    static final int PIX_SRC = 0xc;

    // rotation modes (rotate.h)
    /** Rotate by sampling — keeps a 1 bpp image 1 bpp (no greyscale area-map). */
    static final int L_ROTATE_SAMPLING = 3;

    /** Bring in white at the corners exposed by a rotation. */
    static final int L_BRING_IN_WHITE = 1;

    /** The primary system property to override the resolved Leptonica library path. */
    static final String LIB_PATH_PROPERTY = "p4suta.leptonica.path";

    // Override properties in priority order: the canonical property first, then the older per-app
    // keys `register.leptonica.path` / `despeckle.leptonica.path` (kept for backward
    // compatibility).
    // Standard multiarch locations are probed last. NB: this is the REVERSE of ToolPath.resolve,
    // which tries the per-app key first — library loading prefers the canonical key, external-tool
    // lookup prefers the per-app key.
    private static final List<String> LIB_PATH_PROPERTIES =
            List.of(LIB_PATH_PROPERTY, "register.leptonica.path", "despeckle.leptonica.path");

    // System.load + loaderLookup, the non-deprecated counterpart to libraryLookup (which JDK 25
    // marks for removal). System.load wants an absolute path, so resolveLibraryPath resolves the
    // versioned soname across the standard multiarch locations.
    private static final SymbolLookup LEPT = loadLeptonica();
    private static final Linker LINKER = Linker.nativeLinker();

    private static SymbolLookup loadLeptonica() {
        String libraryPath = resolveLibraryPath();
        preloadColocatedDependencies(libraryPath);
        System.load(libraryPath);
        return SymbolLookup.loaderLookup();
    }

    /**
     * Windows only: load Leptonica's co-located dependency DLLs into the process before Leptonica
     * itself. Unlike Linux ($ORIGIN RUNPATH) and macOS (@loader_path), the Windows loader does NOT
     * search a {@code System.load}'d DLL's own directory for its dependencies — only the process
     * search path — so a self-contained bundle that co-locates Leptonica's whole closure beside it
     * would still fail to resolve {@code libpng}/{@code libtiff}/… at load time. Pre-loading each
     * sibling DLL by absolute path sidesteps the search-path question entirely: once a dependency
     * is mapped into the process, the loader satisfies a later module's import by base name without
     * any path lookup. Repeated passes drive the topological order (leaf libraries load first) and
     * the loop converges because the bundle's closure is complete; a sibling that never loads (e.g.
     * an unrelated tool DLL) is skipped without affecting Leptonica's own subtree. No-op off
     * Windows.
     */
    private static void preloadColocatedDependencies(String libraryPath) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }
        Path library = Path.of(libraryPath);
        Path dir = library.getParent();
        if (dir == null) {
            return;
        }
        List<Path> pending = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
                    .filter(p -> !p.equals(library))
                    .forEach(pending::add);
        } catch (IOException e) {
            return; // best-effort; the System.load below surfaces any genuine failure
        }
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            Iterator<Path> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Path dll = iterator.next();
                try {
                    System.load(dll.toString());
                    iterator.remove();
                    progress = true;
                } catch (UnsatisfiedLinkError dependencyNotLoadedYet) {
                    // A dependency of this DLL is not in the process yet; a later pass will reach
                    // it.
                }
            }
        }
    }

    private static String resolveLibraryPath() {
        for (String property : LIB_PATH_PROPERTIES) {
            String override = System.getProperty(property);
            if (override != null) {
                return override;
            }
        }
        List<String> candidates = candidatePaths();
        for (String candidate : candidates) {
            if (Files.exists(Path.of(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Leptonica shared library not found; tried "
                        + candidates
                        + " (override with -D"
                        + LIB_PATH_PROPERTY
                        + "=/path/to/liblept.so)");
    }

    private static List<String> candidatePaths() {
        String triplet =
                switch (System.getProperty("os.arch", "")) {
                    case "amd64", "x86_64" -> "x86_64-linux-gnu";
                    case "aarch64", "arm64" -> "aarch64-linux-gnu";
                    default -> null;
                };
        List<String> candidates = new ArrayList<>();
        // The runtime package ships the versioned soname; the bare symlink comes from the -dev
        // package. Prefer the versioned name so a runtime-only image (no -dev) still resolves.
        for (String soname : List.of("liblept.so.5", "liblept.so")) {
            if (triplet != null) {
                candidates.add("/usr/lib/" + triplet + "/" + soname);
                candidates.add("/lib/" + triplet + "/" + soname);
            }
            candidates.add("/usr/lib/" + soname);
            candidates.add("/usr/local/lib/" + soname);
        }
        return candidates;
    }

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol =
                LEPT.find(name)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Leptonica symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    // pix lifecycle / metadata
    private static final MethodHandle PIX_READ =
            handle("pixRead", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle PIX_WRITE =
            handle("pixWrite", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle PIX_WRITE_WEBP =
            handle(
                    "pixWriteWebP",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_DESTROY =
            handle("pixDestroy", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle PIX_GET_WIDTH =
            handle("pixGetWidth", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle PIX_GET_HEIGHT =
            handle("pixGetHeight", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle PIX_GET_INPUT_FORMAT =
            handle("pixGetInputFormat", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle PIX_GET_X_RES =
            handle("pixGetXRes", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle PIX_SET_RESOLUTION =
            handle(
                    "pixSetResolution",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

    // projection profiles (NUMA of per-row / per-column ink counts)
    private static final MethodHandle PIX_COUNT_PIXELS_BY_ROW =
            handle("pixCountPixelsByRow", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_COUNT_PIXELS_BY_COLUMN =
            handle("pixCountPixelsByColumn", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle NUMA_GET_COUNT =
            handle("numaGetCount", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle NUMA_GET_I_VALUE =
            handle("numaGetIValue", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle NUMA_DESTROY =
            handle("numaDestroy", FunctionDescriptor.ofVoid(ADDRESS));

    // geometry: crop, canvas, blit
    private static final MethodHandle BOX_CREATE =
            handle(
                    "boxCreate",
                    FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle BOX_DESTROY =
            handle("boxDestroy", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle PIX_CLIP_RECTANGLE =
            handle("pixClipRectangle", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_CREATE =
            handle("pixCreate", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_RASTEROP =
            handle(
                    "pixRasterop",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                            ADDRESS, JAVA_INT, JAVA_INT));

    // deskew + scale
    private static final MethodHandle PIX_ROTATE_ORTH =
            handle("pixRotateOrth", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle PIX_DESKEW =
            handle("pixDeskew", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle PIX_SCALE_TO_SIZE =
            handle("pixScaleToSize", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_FIND_SKEW =
            handle("pixFindSkew", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_ROTATE =
            handle(
                    "pixRotate",
                    FunctionDescriptor.of(
                            ADDRESS, ADDRESS, JAVA_FLOAT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

    // boolean / morphology / size-select / counting
    private static final MethodHandle PIX_INVERT =
            handle("pixInvert", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_SUBTRACT =
            handle("pixSubtract", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_DILATE_BRICK =
            handle(
                    "pixDilateBrick",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_OPEN_BRICK =
            handle(
                    "pixOpenBrick",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_DILATE_BRICK_DWA =
            handle(
                    "pixDilateBrickDwa",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle PIX_OPEN_BRICK_DWA =
            handle(
                    "pixOpenBrickDwa",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle MAKE_PIXEL_SUM_TAB8 =
            handle("makePixelSumTab8", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle PIX_AND =
            handle("pixAnd", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_OR =
            handle("pixOr", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_EQUAL =
            handle("pixEqual", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_COUNT_CONN_COMP =
            handle("pixCountConnComp", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle PIX_COUNT_PIXELS =
            handle("pixCountPixels", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PIX_SELECT_BY_SIZE =
            handle(
                    "pixSelectBySize",
                    FunctionDescriptor.of(
                            ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                            ADDRESS));

    private static final MethodHandle SET_MSG_SEVERITY =
            handle("setMsgSeverity", FunctionDescriptor.of(JAVA_INT, JAVA_INT));

    /**
     * Largest brick side routed to a single DWA kernel call. Leptonica generates DWA sels only for
     * a fixed set of linear sizes; measured against this build's 1.82, {@code pixDilateBrickDwa}
     * silently diverges from the generic brick for the missing sizes (every prime above 15, e.g.
     * 17, 43), while sizes up to 15 are complete. Larger dilations are composed from safe passes in
     * {@code Pix}; the equality sweep in {@code PixTest} pins all of this empirically.
     */
    static final int DWA_SAFE_BRICK = 15;

    /**
     * The process-lifetime 8-bit popcount table {@code pixCountPixels} consumes (~1 KiB native,
     * deliberately never freed): without it Leptonica mallocs, fills and frees the same table on
     * every call.
     */
    private static final MemorySegment PIXEL_SUM_TAB8;

    static {
        // Suppress Leptonica's stderr diagnostics once, at class load. The returned previous
        // severity is discarded.
        try {
            SET_MSG_SEVERITY.invoke(L_SEVERITY_NONE);
            PIXEL_SUM_TAB8 = (MemorySegment) MAKE_PIXEL_SUM_TAB8.invoke();
        } catch (Throwable t) {
            throw sneaky("leptonica static init", t);
        }
    }

    /** {@return the shared popcount table for {@code pixCountPixels}} */
    static MemorySegment pixelSumTab8() {
        return PIXEL_SUM_TAB8;
    }

    // pix lifecycle / metadata

    /** Read an image file, returning the raw {@code PIX *} (0 on failure). */
    static MemorySegment pixRead(MemorySegment filename) {
        try {
            return (MemorySegment) PIX_READ.invoke(filename);
        } catch (Throwable t) {
            throw sneaky("pixRead", t);
        }
    }

    /** Write {@code pix} to {@code filename} in {@code format}; returns 0 on success. */
    static int pixWrite(MemorySegment filename, MemorySegment pix, int format) {
        try {
            return (int) PIX_WRITE.invoke(filename, pix, format);
        } catch (Throwable t) {
            throw sneaky("pixWrite", t);
        }
    }

    /**
     * Write {@code pix} to {@code filename} as WebP; returns 0 on success. The dedicated WebP
     * writer (not the generic {@link #pixWrite}, which is lossy by default), so {@code lossless} is
     * honored. With {@code lossless != 0}, {@code quality} is the libwebp lossless effort level.
     */
    static int pixWriteWebP(MemorySegment filename, MemorySegment pix, int quality, int lossless) {
        try {
            return (int) PIX_WRITE_WEBP.invoke(filename, pix, quality, lossless);
        } catch (Throwable t) {
            throw sneaky("pixWriteWebP", t);
        }
    }

    /** Free a {@code PIX}; {@code ppix} is a {@code PIX **} slot, nulled on return. */
    static void pixDestroy(MemorySegment ppix) {
        try {
            PIX_DESTROY.invoke(ppix);
        } catch (Throwable t) {
            throw sneaky("pixDestroy", t);
        }
    }

    static int pixGetWidth(MemorySegment pix) {
        try {
            return (int) PIX_GET_WIDTH.invoke(pix);
        } catch (Throwable t) {
            throw sneaky("pixGetWidth", t);
        }
    }

    static int pixGetHeight(MemorySegment pix) {
        try {
            return (int) PIX_GET_HEIGHT.invoke(pix);
        } catch (Throwable t) {
            throw sneaky("pixGetHeight", t);
        }
    }

    static int pixGetInputFormat(MemorySegment pix) {
        try {
            return (int) PIX_GET_INPUT_FORMAT.invoke(pix);
        } catch (Throwable t) {
            throw sneaky("pixGetInputFormat", t);
        }
    }

    /** The image's horizontal resolution in DPI, or 0 if the source carried none. */
    static int pixGetXRes(MemorySegment pix) {
        try {
            return (int) PIX_GET_X_RES.invoke(pix);
        } catch (Throwable t) {
            throw sneaky("pixGetXRes", t);
        }
    }

    /** Set both axes' resolution in DPI so a format that records it writes an accurate tag. */
    static int pixSetResolution(MemorySegment pix, int xres, int yres) {
        try {
            return (int) PIX_SET_RESOLUTION.invoke(pix, xres, yres);
        } catch (Throwable t) {
            throw sneaky("pixSetResolution", t);
        }
    }

    // projection profiles

    /** A {@code NUMA *} of the per-row foreground-pixel counts ({@code tab8} may be NULL). */
    static MemorySegment pixCountPixelsByRow(MemorySegment pix) {
        try {
            return (MemorySegment) PIX_COUNT_PIXELS_BY_ROW.invoke(pix, MemorySegment.NULL);
        } catch (Throwable t) {
            throw sneaky("pixCountPixelsByRow", t);
        }
    }

    /** A {@code NUMA *} of the per-column foreground-pixel counts. */
    static MemorySegment pixCountPixelsByColumn(MemorySegment pix) {
        try {
            return (MemorySegment) PIX_COUNT_PIXELS_BY_COLUMN.invoke(pix);
        } catch (Throwable t) {
            throw sneaky("pixCountPixelsByColumn", t);
        }
    }

    static int numaGetCount(MemorySegment numa) {
        try {
            return (int) NUMA_GET_COUNT.invoke(numa);
        } catch (Throwable t) {
            throw sneaky("numaGetCount", t);
        }
    }

    /** Read element {@code index} (rounded to int) into {@code pival}; returns 0 on success. */
    static int numaGetIValue(MemorySegment numa, int index, MemorySegment pival) {
        try {
            return (int) NUMA_GET_I_VALUE.invoke(numa, index, pival);
        } catch (Throwable t) {
            throw sneaky("numaGetIValue", t);
        }
    }

    /** Free a {@code NUMA}; {@code pnuma} is a {@code NUMA **} slot, nulled on return. */
    static void numaDestroy(MemorySegment pnuma) {
        try {
            NUMA_DESTROY.invoke(pnuma);
        } catch (Throwable t) {
            throw sneaky("numaDestroy", t);
        }
    }

    // geometry

    /** Allocate a {@code BOX} with the given geometry. */
    static MemorySegment boxCreate(int x, int y, int w, int h) {
        try {
            return (MemorySegment) BOX_CREATE.invoke(x, y, w, h);
        } catch (Throwable t) {
            throw sneaky("boxCreate", t);
        }
    }

    /** Free a {@code BOX}; {@code pbox} is a {@code BOX **} slot, nulled on return. */
    static void boxDestroy(MemorySegment pbox) {
        try {
            BOX_DESTROY.invoke(pbox);
        } catch (Throwable t) {
            throw sneaky("boxDestroy", t);
        }
    }

    /**
     * Crop {@code pixs} to {@code box} into a fresh {@code PIX} (the {@code pboxc == NULL} path).
     */
    static MemorySegment pixClipRectangle(MemorySegment pixs, MemorySegment box) {
        try {
            return (MemorySegment) PIX_CLIP_RECTANGLE.invoke(pixs, box, MemorySegment.NULL);
        } catch (Throwable t) {
            throw sneaky("pixClipRectangle", t);
        }
    }

    /** Allocate a {@code depth}-bpp {@code PIX}; a fresh 1 bpp image is all-0 (white). */
    static MemorySegment pixCreate(int width, int height, int depth) {
        try {
            return (MemorySegment) PIX_CREATE.invoke(width, height, depth);
        } catch (Throwable t) {
            throw sneaky("pixCreate", t);
        }
    }

    /** Blit a {@code dw x dh} region from {@code pixs(sx,sy)} onto {@code pixd(dx,dy)}; clips. */
    static int pixRasterop(
            MemorySegment pixd,
            int dx,
            int dy,
            int dw,
            int dh,
            int op,
            MemorySegment pixs,
            int sx,
            int sy) {
        try {
            return (int) PIX_RASTEROP.invoke(pixd, dx, dy, dw, dh, op, pixs, sx, sy);
        } catch (Throwable t) {
            throw sneaky("pixRasterop", t);
        }
    }

    // deskew + scale

    /** Rotate {@code pixs} by {@code quads} 90-degree turns clockwise into a fresh {@code PIX}. */
    static MemorySegment pixRotateOrth(MemorySegment pixs, int quads) {
        try {
            return (MemorySegment) PIX_ROTATE_ORTH.invoke(pixs, quads);
        } catch (Throwable t) {
            throw sneaky("pixRotateOrth", t);
        }
    }

    /**
     * Deskew {@code pixs} into a fresh {@code PIX}, or a clone if no reliable skew was found.
     * {@code redsearch} of 0 selects Leptonica's default reduction. The confidence-gated deskew
     * policy that uses it lives app-side.
     */
    static MemorySegment pixDeskew(MemorySegment pixs, int redsearch) {
        try {
            return (MemorySegment) PIX_DESKEW.invoke(pixs, redsearch);
        } catch (Throwable t) {
            throw sneaky("pixDeskew", t);
        }
    }

    /** Scale {@code pixs} to {@code wd x hd}; a 0 dimension preserves aspect from the other. */
    static MemorySegment pixScaleToSize(MemorySegment pixs, int wd, int hd) {
        try {
            return (MemorySegment) PIX_SCALE_TO_SIZE.invoke(pixs, wd, hd);
        } catch (Throwable t) {
            throw sneaky("pixScaleToSize", t);
        }
    }

    /**
     * Estimate the skew of {@code pixs} (1 bpp, horizontal text). Writes the angle in degrees into
     * {@code pangle} and a confidence ratio into {@code pconf}; returns 0 on success, 1 if no
     * estimate was made.
     */
    static int pixFindSkew(MemorySegment pixs, MemorySegment pangle, MemorySegment pconf) {
        try {
            return (int) PIX_FIND_SKEW.invoke(pixs, pangle, pconf);
        } catch (Throwable t) {
            throw sneaky("pixFindSkew", t);
        }
    }

    /**
     * Rotate {@code pixs} by {@code angle} radians (positive clockwise) into a fresh {@code PIX}.
     */
    static MemorySegment pixRotate(
            MemorySegment pixs, float angle, int type, int incolor, int width, int height) {
        try {
            return (MemorySegment) PIX_ROTATE.invoke(pixs, angle, type, incolor, width, height);
        } catch (Throwable t) {
            throw sneaky("pixRotate", t);
        }
    }

    // boolean / morphology / size-select / counting

    /** Invert {@code src} into a fresh {@code PIX} (the {@code pixd == NULL} path). */
    static MemorySegment pixInvert(MemorySegment src) {
        try {
            return (MemorySegment) PIX_INVERT.invoke(MemorySegment.NULL, src);
        } catch (Throwable t) {
            throw sneaky("pixInvert", t);
        }
    }

    /** {@code s1 AND NOT s2} into a fresh {@code PIX} (the {@code pixd == NULL} path). */
    static MemorySegment pixSubtract(MemorySegment s1, MemorySegment s2) {
        try {
            return (MemorySegment) PIX_SUBTRACT.invoke(MemorySegment.NULL, s1, s2);
        } catch (Throwable t) {
            throw sneaky("pixSubtract", t);
        }
    }

    /** Dilate {@code src} by a {@code hsize x vsize} brick (odd sizes) into a fresh {@code PIX}. */
    static MemorySegment pixDilateBrick(MemorySegment src, int hsize, int vsize) {
        try {
            return (MemorySegment) PIX_DILATE_BRICK.invoke(MemorySegment.NULL, src, hsize, vsize);
        } catch (Throwable t) {
            throw sneaky("pixDilateBrick", t);
        }
    }

    /**
     * Open (erode then dilate) {@code src} by a {@code hsize x vsize} brick into a fresh {@code
     * PIX}.
     */
    static MemorySegment pixOpenBrick(MemorySegment src, int hsize, int vsize) {
        try {
            return (MemorySegment) PIX_OPEN_BRICK.invoke(MemorySegment.NULL, src, hsize, vsize);
        } catch (Throwable t) {
            throw sneaky("pixOpenBrick", t);
        }
    }

    /**
     * Dilate {@code src} by a {@code hsize x vsize} brick via the word-accelerated DWA kernels into
     * a fresh {@code PIX}. Only exact for sizes up to {@link #DWA_SAFE_BRICK} — see that constant;
     * {@code Pix} owns the larger-size composition.
     */
    static MemorySegment pixDilateBrickDwa(MemorySegment src, int hsize, int vsize) {
        try {
            return (MemorySegment)
                    PIX_DILATE_BRICK_DWA.invoke(MemorySegment.NULL, src, hsize, vsize);
        } catch (Throwable t) {
            throw sneaky("pixDilateBrickDwa", t);
        }
    }

    /**
     * Open (erode then dilate) {@code src} by a {@code hsize x vsize} brick via the
     * word-accelerated DWA kernels into a fresh {@code PIX}. Routed only for sizes up to {@link
     * #DWA_SAFE_BRICK}; pixel-identical there to {@link #pixOpenBrick} (pinned by {@code PixTest}'s
     * sweep) and several times faster.
     */
    static MemorySegment pixOpenBrickDwa(MemorySegment src, int hsize, int vsize) {
        try {
            return (MemorySegment) PIX_OPEN_BRICK_DWA.invoke(MemorySegment.NULL, src, hsize, vsize);
        } catch (Throwable t) {
            throw sneaky("pixOpenBrickDwa", t);
        }
    }

    /** {@code s1 AND s2} into a fresh {@code PIX} (the {@code pixd == NULL} path). */
    static MemorySegment pixAnd(MemorySegment s1, MemorySegment s2) {
        try {
            return (MemorySegment) PIX_AND.invoke(MemorySegment.NULL, s1, s2);
        } catch (Throwable t) {
            throw sneaky("pixAnd", t);
        }
    }

    /** {@code s1 OR s2} into a fresh {@code PIX} (the {@code pixd == NULL} path). */
    static MemorySegment pixOr(MemorySegment s1, MemorySegment s2) {
        try {
            return (MemorySegment) PIX_OR.invoke(MemorySegment.NULL, s1, s2);
        } catch (Throwable t) {
            throw sneaky("pixOr", t);
        }
    }

    /** Whether two images are pixel-identical; writes 1/0 into {@code psame}. */
    static int pixEqual(MemorySegment a, MemorySegment b, MemorySegment psame) {
        try {
            return (int) PIX_EQUAL.invoke(a, b, psame);
        } catch (Throwable t) {
            throw sneaky("pixEqual", t);
        }
    }

    static int pixCountConnComp(MemorySegment pix, int connectivity, MemorySegment pcount) {
        try {
            return (int) PIX_COUNT_CONN_COMP.invoke(pix, connectivity, pcount);
        } catch (Throwable t) {
            throw sneaky("pixCountConnComp", t);
        }
    }

    static int pixCountPixels(MemorySegment pix, MemorySegment pcount, MemorySegment tab8) {
        try {
            return (int) PIX_COUNT_PIXELS.invoke(pix, pcount, tab8);
        } catch (Throwable t) {
            throw sneaky("pixCountPixels", t);
        }
    }

    /** Returns a new {@code PIX} of the components that satisfy the size constraint. */
    static MemorySegment pixSelectBySize(
            MemorySegment pix,
            int width,
            int height,
            int connectivity,
            int type,
            int relation,
            MemorySegment pchanged) {
        try {
            return (MemorySegment)
                    PIX_SELECT_BY_SIZE.invoke(
                            pix, width, height, connectivity, type, relation, pchanged);
        } catch (Throwable t) {
            throw sneaky("pixSelectBySize", t);
        }
    }

    private static RuntimeException sneaky(String fn, Throwable cause) {
        return new IllegalStateException("Leptonica call failed: " + fn, cause);
    }
}
