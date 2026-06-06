package io.github.p4suta.webapp.port;

import io.github.p4suta.webapp.domain.JobId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Owns each job's private working directory, where the upload is stored and the result is written.
 * Independent of the pipeline's own per-run temp area: these files persist until the result is
 * downloaded or the reaper removes them.
 */
public interface Workspace {

    /**
     * Creates the private directory for {@code id}.
     *
     * @param id the job id
     * @throws IOException if the directory cannot be created
     */
    void allocate(JobId id) throws IOException;

    /**
     * Stores the uploaded PDF for {@code id}, consuming {@code pdf}, and returns its content hash.
     * The bytes are verified to begin with the PDF magic ({@code %PDF-}) and digested as they are
     * written, so one streaming pass validates and hashes a large upload without buffering it in
     * memory.
     *
     * @param id the job id
     * @param pdf the uploaded bytes
     * @return the lowercase-hex SHA-256 of the stored bytes
     * @throws IOException if the upload cannot be written
     * @throws IllegalArgumentException if the upload is not a PDF
     */
    String storeUpload(JobId id, InputStream pdf) throws IOException;

    /**
     * Places {@code source} as {@code id}'s result, hard-linking it where possible (same
     * filesystem) and copying otherwise. Used to serve a cached result through the normal result
     * path without re-running the conversion.
     *
     * @param id the job id
     * @param source the finished result to expose as this job's output
     * @throws IOException if the result cannot be placed
     */
    void placeResult(JobId id, Path source) throws IOException;

    /**
     * {@return the path the upload is (or will be) stored at}
     *
     * @param id the job id
     */
    Path inputPdf(JobId id);

    /**
     * {@return the path the result is (or will be) written at}
     *
     * @param id the job id
     */
    Path outputPdf(JobId id);

    /**
     * {@return the result path if it exists on disk, else empty}
     *
     * @param id the job id
     */
    Optional<Path> resultIfPresent(JobId id);

    /**
     * Deletes {@code id}'s working directory and everything in it.
     *
     * @param id the job id
     * @throws IOException if removal fails
     */
    void remove(JobId id) throws IOException;
}
