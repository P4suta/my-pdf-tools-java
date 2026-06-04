/**
 * The register domain exception model: {@link
 * io.github.p4suta.register.domain.exception.RegisterErrorKind} (the app-specific failure
 * categories implementing the shared {@link io.github.p4suta.shared.kernel.error.ErrorCategory})
 * and {@link io.github.p4suta.register.domain.exception.RegisterException} (the unchecked exception
 * that carries a kind to the CLI boundary, where the shared {@code ExceptionMapper} turns it into a
 * sysexits exit code and log level). Generic failures reuse {@link
 * io.github.p4suta.shared.kernel.error.CommonErrorKind} rather than adding a kind here.
 */
@NullMarked
package io.github.p4suta.register.domain.exception;

import org.jspecify.annotations.NullMarked;
