/**
 * External-process plumbing. Depends only on the SLF4J facade; no domain exception is reachable
 * here.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.process.ToolPath} resolves an external tool to a {@link
 *       java.nio.file.Path}: an explicit {@code -D<property>} override wins, else the first
 *       executable of that name on {@code PATH}. Returns an {@link java.util.Optional} so the
 *       tool-missing policy (fatal vs optional skip) stays with the caller; the property key is a
 *       parameter so each app passes its own {@code register.jbig2.path} / {@code
 *       despeckle.qpdf.path};
 *   <li>{@link io.github.p4suta.shared.process.ProcessRunner} runs a command to completion under a
 *       timeout and returns a {@link io.github.p4suta.shared.process.ProcessRunner.Result} (exit
 *       code, captured stdout, captured stderr, elapsed {@link java.time.Duration}); it throws
 *       {@link java.util.concurrent.TimeoutException} on timeout, and the caller supplies the set
 *       of acceptable non-zero exit codes (e.g. {@code qpdf}'s exit 3 for "success with warnings");
 *   <li>{@link io.github.p4suta.shared.process.Tasks} fans a list of {@link
 *       java.util.concurrent.Callable}s out across a caller-owned {@link
 *       java.util.concurrent.ExecutorService}, collects their results in submission order, and
 *       aggregates the first failure into a single {@link java.io.IOException}.
 * </ul>
 */
@NullMarked
package io.github.p4suta.shared.process;

import org.jspecify.annotations.NullMarked;
