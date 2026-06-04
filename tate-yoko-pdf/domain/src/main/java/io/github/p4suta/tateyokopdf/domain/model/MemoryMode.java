package io.github.p4suta.tateyokopdf.domain.model;

/**
 * How the PDF backend should cache document streams while a spread is built.
 *
 * <p>Composing a spread clones each source page's streams into the output document. {@link
 * #IN_MEMORY} keeps those clones on the heap — fastest, but resident memory grows roughly with the
 * input size, so a very large scan can exhaust the heap. {@link #SCRATCH_FILE} spills them to a
 * temporary file instead, bounding heap use at the cost of extra disk I/O.
 *
 * <p>The default is {@link #IN_MEMORY}, kept safe on ordinary machines by the launcher's
 * RAM-proportional heap. The CLI selects {@link #SCRATCH_FILE} via {@code --low-memory} for the
 * rare case of converting an enormous PDF on a memory-constrained host.
 */
public enum MemoryMode {
    IN_MEMORY,
    SCRATCH_FILE
}
