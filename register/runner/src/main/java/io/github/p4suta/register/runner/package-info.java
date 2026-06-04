/**
 * The corpus orchestration layer — the one place that walks the filesystem and drives the worker
 * pool. {@link io.github.p4suta.register.runner.Runner} registers a directory of pages in two
 * passes: analyze every page to a per-parity median {@link
 * io.github.p4suta.register.core.Reference}, then place each page against it, mirroring the input
 * layout into the output directory. Keeping the I/O and threading here leaves {@code core} a pure,
 * reusable kernel.
 */
@NullMarked
package io.github.p4suta.register.runner;

import org.jspecify.annotations.NullMarked;
