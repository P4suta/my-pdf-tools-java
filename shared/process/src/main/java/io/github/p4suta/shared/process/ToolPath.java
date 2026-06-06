package io.github.p4suta.shared.process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates an external command-line tool: an explicit {@code -D<property>} override wins, otherwise
 * the first executable of that name on {@code PATH}. Returns an {@link Optional} so each caller
 * picks its own "not found" policy.
 *
 * <p>The override property key is a parameter, not a unified constant: each app passes its own
 * ({@code register.jbig2.path}, {@code despeckle.qpdf.path}, …).
 *
 * <p>Native-library loading (resolving an absolute {@code .so} for {@code System.load}) is not
 * routed through here; it lives in {@code io.github.p4suta.shared.imaging}.
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
     *     register.jbig2.path})
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
