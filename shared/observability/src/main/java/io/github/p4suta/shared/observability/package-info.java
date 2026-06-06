/**
 * The framework-touching layer the dependency-free {@code :shared:kernel} excludes. {@link
 * io.github.p4suta.shared.observability.ExceptionMapper} turns any throwable into a stable {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory}, a sysexits exit code, an slf4j {@code
 * Level}, and a PII-safe user message — reading the exit code and severity off the {@code
 * ErrorCategory} and owning the single {@code Severity -> Level} translation. {@link
 * io.github.p4suta.shared.observability.PiiSanitizer} masks absolute paths out of log-bound text;
 * {@link io.github.p4suta.shared.observability.FatalUncaughtHandler} is the JVM-wide
 * uncaught-exception handler that exits on OOM; {@link
 * io.github.p4suta.shared.observability.ExitCodes} is the sysexits registry. Depends only on {@code
 * :shared:kernel} and the slf4j facade.
 */
@NullMarked
package io.github.p4suta.shared.observability;

import org.jspecify.annotations.NullMarked;
