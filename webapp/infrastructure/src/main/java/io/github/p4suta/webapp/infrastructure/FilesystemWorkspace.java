package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link Workspace} that gives each job a private directory under a root, holding the uploaded
 * {@code input.pdf} and the produced {@code output.pdf}. Job ids are already restricted to a safe
 * token, but each resolved directory is re-checked to lie under the root as defence in depth
 * against path traversal.
 */
public final class FilesystemWorkspace implements Workspace {

    private final Path root;

    /**
     * @param root the base directory under which each job gets a subdirectory
     */
    public FilesystemWorkspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path dir(JobId id) {
        Path resolved = root.resolve(id.value()).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("unsafe job id: " + id.value());
        }
        return resolved;
    }

    @Override
    public void allocate(JobId id) throws IOException {
        Files.createDirectories(dir(id));
    }

    @Override
    public void storeUpload(JobId id, InputStream pdf) throws IOException {
        Files.copy(pdf, inputPdf(id), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Path inputPdf(JobId id) {
        return dir(id).resolve("input.pdf");
    }

    @Override
    public Path outputPdf(JobId id) {
        return dir(id).resolve("output.pdf");
    }

    @Override
    public Optional<Path> resultIfPresent(JobId id) {
        Path output = outputPdf(id);
        return Files.isRegularFile(output) ? Optional.of(output) : Optional.empty();
    }

    @Override
    public void remove(JobId id) throws IOException {
        Path target = dir(id);
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
