package io.github.p4suta.webapp.port;

import io.github.p4suta.webapp.domain.JobId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Owns each job's private working directory — where the upload is stored and the result is written
 * — with a lifecycle independent of the pipeline's own per-run temp area: these files persist until
 * the result is downloaded or the reaper removes them.
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
     * Stores the uploaded PDF for {@code id}, consuming {@code pdf}.
     *
     * @param id the job id
     * @param pdf the uploaded bytes
     * @throws IOException if the upload cannot be written
     */
    void storeUpload(JobId id, InputStream pdf) throws IOException;

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
