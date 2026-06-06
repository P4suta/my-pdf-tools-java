package io.github.p4suta.webapp.application;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    public String storeUpload(JobId id, InputStream pdf) throws IOException {
        byte[] bytes = pdf.readAllBytes(); // tests use tiny inputs
        Files.write(inputPdf(id), bytes);
        return sha256Hex(bytes);
    }

    @Override
    public void placeResult(JobId id, Path source) throws IOException {
        Files.copy(source, outputPdf(id), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
