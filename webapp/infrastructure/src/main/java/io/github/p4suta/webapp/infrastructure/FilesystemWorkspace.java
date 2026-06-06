package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.Workspace;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link Workspace} that gives each job a private directory under a root, holding the uploaded
 * {@code input.pdf} and the produced {@code output.pdf}. Job ids are already restricted to a safe
 * token, but each resolved directory is re-checked to lie under the root as defense in depth
 * against path traversal.
 */
public final class FilesystemWorkspace implements Workspace {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

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
    public String storeUpload(JobId id, InputStream pdf) throws IOException {
        MessageDigest digest = sha256();
        BufferedInputStream buffered = new BufferedInputStream(pdf);
        // Peek the first bytes to reject non-PDF uploads before writing the whole file; reset and
        // then digest every byte as Files.copy streams it to disk (no in-memory buffering).
        buffered.mark(PDF_MAGIC.length + 1);
        byte[] head = buffered.readNBytes(PDF_MAGIC.length);
        if (!Arrays.equals(head, PDF_MAGIC)) {
            throw new IllegalArgumentException("the upload is not a PDF (missing %PDF- header)");
        }
        buffered.reset();
        try (DigestInputStream digesting = new DigestInputStream(buffered, digest)) {
            Files.copy(digesting, inputPdf(id), StandardCopyOption.REPLACE_EXISTING);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public void placeResult(JobId id, Path source) throws IOException {
        // The job directory already exists (allocate was called); just (re)place output.pdf in it.
        Path dest = outputPdf(id);
        Files.deleteIfExists(dest);
        try {
            // Same filesystem (cache root is a sibling of the work dir): share the bytes by hard
            // link.
            Files.createLink(dest, source);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // Links unsupported or across devices (EXDEV): copy instead.
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
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
