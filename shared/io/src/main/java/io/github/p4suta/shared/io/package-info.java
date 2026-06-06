/**
 * Filesystem-orchestration helpers for walking an input corpus and mirroring it into an output
 * tree. Public signatures depend only on the JDK ({@link java.nio.file.Path}, {@link
 * java.io.IOException}); the SLF4J facade is the single declared dependency.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.io.OutputDirs#prepare} ensures the output directory exists
 *       and rejects a non-empty one unless {@code force}, with the exact operator message app tests
 *       assert;
 *   <li>{@link io.github.p4suta.shared.io.CorpusFiles#collect} returns every regular file under a
 *       root whose name matches a caller-supplied glob, sorted by full path string for a
 *       deterministic processing order; the glob is a parameter, not a hardcoded extension set;
 *   <li>{@link io.github.p4suta.shared.io.CorpusFiles#mirrorDestination} resolves a source's
 *       input-relative path under an output root and swaps its extension to a caller-supplied one
 *       (or keeps it when {@code null}); the replacement extension is a {@link
 *       org.jspecify.annotations.Nullable} {@link java.lang.String} parameter, so this module
 *       imports no app's output-format enum.
 * </ul>
 */
@NullMarked
package io.github.p4suta.shared.io;

import org.jspecify.annotations.NullMarked;
