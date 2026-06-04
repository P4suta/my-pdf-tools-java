package io.github.p4suta.shared.process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates an external command-line tool the same way across the I/O layers: an explicit {@code
 * -D<property>} override wins (this is how the packaged app-image points at its bundled binaries),
 * otherwise the first executable of that name on {@code PATH}. Returns an {@link Optional} so each
 * caller picks its own policy for "not found" — register's PDF pipeline treats a missing tool as
 * fatal, while its diagnostics flip-book treats it as an optional skip.
 *
 * <p>The override property KEY is a parameter, deliberately not a unified constant: each app passes
 * its own ({@code register.jbig2.path}, {@code despeckle.qpdf.path}, …) so a packaged app-image
 * keeps resolving its bundled binaries through the {@code -D} key its launcher already sets.
 *
 * <p>Native-library loading (resolving an absolute {@code .so} for {@code System.load}) is a
 * different concern and is deliberately NOT routed through here — it lives in the {@code
 * io.github.p4suta.shared.imaging} FFM island.
 */
public final class ToolPath {

    private ToolPath() {}

    /**
     * Resolve {@code tool} to an absolute {@link Path}: the value of {@code -D<propertyKey>} when
     * set and non-blank, else the first executable named {@code tool} found on {@code PATH}, else
     * empty.
     *
     * @param tool the executable's name (e.g. {@code jbig2})
     * @param propertyKey the system-property override to consult first (e.g. {@code
     *     register.jbig2.path}); each app passes its own, so the keys are never unified
     * @return the resolved path, or empty when neither the override nor {@code PATH} yields one
     */
    public static Optional<Path> resolve(String tool, String propertyKey) {
        String override = System.getProperty(propertyKey);
        if (override != null && !override.isBlank()) {
            return Optional.of(Path.of(override));
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
}
