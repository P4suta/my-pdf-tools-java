package io.github.p4suta.webapp.application;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A filesystem-backed {@link Workspace} rooted at a temp dir, with a switch to fail removal. */
final class FakeWorkspace implements Workspace {

    private final Path base;
    boolean failRemove;

    FakeWorkspace(Path base) {
        this.base = base;
    }

    private Path dir(JobId id) {
        return base.resolve(id.value());
    }

    @Override
    public void allocate(JobId id) throws IOException {
        Files.createDirectories(dir(id));
    }

    @Override
    public void storeUpload(JobId id, InputStream pdf) throws IOException {
        Files.copy(pdf, inputPdf(id));
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
        Path out = outputPdf(id);
        return Files.exists(out) ? Optional.of(out) : Optional.empty();
    }

    @Override
    public void remove(JobId id) throws IOException {
        if (failRemove) {
            throw new IOException("cannot remove " + id.value());
        }
        Files.deleteIfExists(outputPdf(id));
        Files.deleteIfExists(inputPdf(id));
        Files.deleteIfExists(dir(id));
    }
}
