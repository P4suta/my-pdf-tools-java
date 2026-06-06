/**
 * CLI scaffolding over Apache Commons CLI. This is the one shared module allowed to write to {@code
 * System.out}/{@code System.err} and to depend on the Apache Commons CLI types; the kernel and
 * observability layers stay stream-free.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.cli.InputResolver} expands the positional arguments into the
 *       concrete inputs — a single file, a directory filtered by a caller-supplied {@code
 *       Predicate<Path>} and sorted by name, or {@code -} for a single stdin input;
 *   <li>{@link io.github.p4suta.shared.cli.StdinSource} drains {@code System.in} into a temp file
 *       (with a caller-chosen prefix/suffix) for the file-based processing path;
 *   <li>{@link io.github.p4suta.shared.cli.OutputTarget} writes to a concrete file, or bridges
 *       stdout through a temp file then streams it to {@code System.out};
 *   <li>{@link io.github.p4suta.shared.cli.BatchDriver} runs a list of items continue-on-error,
 *       aggregating per-item failures through {@link
 *       io.github.p4suta.shared.observability.ExceptionMapper} and returning the sysexits exit
 *       code;
 *   <li>{@link io.github.p4suta.shared.cli.CliExceptionHandler} maps a single throwable to a masked
 *       user message plus a sysexits exit code, keying its OOM hint off the kernel {@link
 *       io.github.p4suta.shared.kernel.error.CommonErrorKind};
 *   <li>{@link io.github.p4suta.shared.cli.CliOptionSupport} holds the parse helpers (int/double
 *       parsing and case-insensitive enum parsing, the positional-count check, and the help /
 *       usage-error renderers).
 * </ul>
 *
 * <p>Depends on {@code :shared:kernel}, {@code :shared:observability}, Apache Commons CLI, and the
 * SLF4J facade.
 */
@NullMarked
package io.github.p4suta.shared.cli;

import org.jspecify.annotations.NullMarked;
