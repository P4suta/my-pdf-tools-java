package io.github.p4suta.register.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builds {@link Reporter} instances. Because a reporter is stateful (it creates its output
 * directory at construction), the application depends on this factory port rather than constructing
 * reporters directly; the {@code :app} composition root supplies the concrete factory.
 */
public interface ReporterFactory {

    /**
     * Create a reporter writing into {@code diagDir}.
     *
     * @param flipbook whether to also assemble the animated-WebP flip-book at finish
     * @throws IOException if the diagnostics directory cannot be created
     */
    Reporter create(Path diagDir, boolean flipbook) throws IOException;

    /** A pass-through reporter for when diagnostics are disabled. */
    Reporter noOp();
}
