package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An action over a filesystem path that may fail with {@link IOException}. Lets {@link
 * OutputTarget} and {@link StdinSource} own the temp-file lifecycle while the caller supplies only
 * the work to run against the concrete path.
 */
@FunctionalInterface
public interface IoPathAction {

    /**
     * Runs the action against {@code path}.
     *
     * @param path the concrete file to act on
     * @throws IOException if the action fails
     */
    void accept(Path path) throws IOException;
}
