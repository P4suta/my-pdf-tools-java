/**
 * The cross-app error model: the dependency-free primitives every p4suta app (register, despeckle,
 * tate-yoko-pdf) shares to classify failures. {@link io.github.p4suta.shared.kernel.error.Severity}
 * is a kernel enum (NOT slf4j {@code Level}, so the kernel stays framework-free); {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory} is the contract a per-app {@code ErrorKind}
 * enum and the shared {@link io.github.p4suta.shared.kernel.error.CommonErrorKind} implement,
 * carrying the user message, client-fault flag, sysexits exit code, and severity ON the constant;
 * {@link io.github.p4suta.shared.kernel.error.BaseAppException} is the abstract unchecked exception
 * each app's domain exception extends. No third-party runtime library — the {@code Severity ->
 * slf4j Level} translation lives only in {@code :shared:observability}.
 */
@NullMarked
package io.github.p4suta.shared.kernel.error;

import org.jspecify.annotations.NullMarked;
