/**
 * Cross-app filesystem-orchestration helpers: the byte-identical glue register's {@code
 * RegistrationService} and despeckle's {@code DespeckleService} each re-derived when they walk an
 * input corpus and mirror it into an output tree. Depends only on the JDK ({@link
 * java.nio.file.Path}, {@link java.io.IOException}) in its public signatures; the SLF4J facade is
 * the single declared dependency.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.io.OutputDirs#prepare} ensures the output directory exists
 *       and rejects a non-empty one unless {@code force}, preserving the EXACT operator message the
 *       apps' end-to-end and unit tests assert;
 *   <li>{@link io.github.p4suta.shared.io.CorpusFiles#collect} returns every regular file under a
 *       root whose NAME matches a caller-supplied glob, sorted by full path string so the
 *       deterministic processing order the apps rely on is preserved — the glob is a PARAMETER, not
 *       a hardcoded extension set;
 *   <li>{@link io.github.p4suta.shared.io.CorpusFiles#mirrorDestination} resolves a source's
 *       input-relative path under an output root and swaps its extension to a caller-supplied one
 *       (or keeps it when {@code null}, i.e. {@code --format same}) — the replacement extension is
 *       a {@link org.jspecify.annotations.Nullable} {@link java.lang.String} parameter, so this
 *       module imports neither app's output-format enum.
 * </ul>
 */
@NullMarked
package io.github.p4suta.shared.io;

import org.jspecify.annotations.NullMarked;
