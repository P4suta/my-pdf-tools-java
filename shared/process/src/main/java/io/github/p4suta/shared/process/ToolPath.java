package io.github.p4suta.shared.process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates an external command-line tool, in priority order: an explicit per-app {@code
 * -D<propertyKey>} override, then the cross-app canonical {@code -Dp4suta.<tool>.path} override,
 * then the first executable of that name on {@code PATH}. Returns an {@link Optional} so each
 * caller picks its own "not found" policy.
 *
 * <p>Two override layers, both additive: the per-app key each call already passes ({@code
 * register.jbig2.path}, {@code despeckle.qpdf.path}, …) keeps resolving as before, and a single
 * {@link #canonicalKey canonical key} ({@code p4suta.jbig2.path}, …) lets one uniform set of {@code
 * -D} options point at a self-contained app-image's bundled binaries regardless of which app
 * launched. The self-contained distribution convention emits only the canonical keys; the per-app
 * keys stay supported so any caller (or test) that sets one still wins.
 *
 * <p>This mirrors {@code io.github.p4suta.shared.imaging.Leptonica}, whose {@code System.load} path
 * resolution already consults the unified {@code p4suta.leptonica.path} ahead of the per-app keys.
 * Native-library loading itself is not routed through here.
 */
public final class ToolPath {

    private ToolPath() {}

    /**
     * The cross-app canonical override key for {@code tool}: {@code p4suta.<tool>.path}. The
     * self-contained distribution convention sets these (e.g. {@code -Dp4suta.pdfimages.path=
     * $APPDIR/natives/pdfimages}) so every app's bundle resolves its binaries through one scheme.
     *
     * @param tool the executable's name (e.g. {@code jbig2})
     * @return the canonical system-property key for that tool
     */
    public static String canonicalKey(String tool) {
        return "p4suta." + tool + ".path";
    }

    /**
     * Resolve {@code tool} to an absolute {@link Path}: the value of {@code -D<propertyKey>} when
     * set and non-blank, else the canonical {@code -Dp4suta.<tool>.path} when set and non-blank,
     * else the first executable named {@code tool} found on {@code PATH}, else empty.
     *
     * @param tool the executable's name (e.g. {@code jbig2})
     * @param propertyKey the per-app system-property override to consult first (e.g. {@code
     *     register.jbig2.path})
     * @return the resolved path, or empty when neither override nor {@code PATH} yields one
     */
    public static Optional<Path> resolve(String tool, String propertyKey) {
        Optional<Path> perApp = fromProperty(propertyKey);
        if (perApp.isPresent()) {
            return perApp;
        }
        Optional<Path> canonical = fromProperty(canonicalKey(tool));
        if (canonical.isPresent()) {
            return canonical;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator, -1)) {
                if (dir.isEmpty()) {
                    continue;
                }
                Path candidate = Path.of(dir, tool);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** The value of {@code -D<key>} as a {@link Path} when set and non-blank, else empty. */
    private static Optional<Path> fromProperty(String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return Optional.of(Path.of(value));
        }
        return Optional.empty();
    }
}
