/**
 * The web feature's driven adapters: {@link
 * io.github.p4suta.webapp.infrastructure.SubprocessConversionEngine} (shells out to the packaged
 * pdfbook binary and tails its progress file), {@link
 * io.github.p4suta.webapp.infrastructure.InMemoryJobStore} (concurrent), {@link
 * io.github.p4suta.webapp.infrastructure.FilesystemWorkspace} (per-job directories), {@link
 * io.github.p4suta.webapp.infrastructure.BoundedConversionExecutor} (a single worker over a bounded
 * queue), and {@link io.github.p4suta.webapp.infrastructure.UuidJobIdGenerator}. No Spring and no
 * FFM: pdfbook runs out-of-process, so this layer never touches the pipeline's code.
 */
@NullMarked
package io.github.p4suta.webapp.infrastructure;

import org.jspecify.annotations.NullMarked;
