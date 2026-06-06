/**
 * The dependency-free error model. {@link io.github.p4suta.shared.kernel.error.Severity} is a
 * kernel enum (not slf4j {@code Level}, so the kernel stays framework-free); {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory} is the contract a per-app {@code ErrorKind}
 * enum and the shared {@link io.github.p4suta.shared.kernel.error.CommonErrorKind} implement,
 * carrying the user message, client-fault flag, sysexits exit code, and severity on the constant;
 * {@link io.github.p4suta.shared.kernel.error.BaseAppException} is the abstract unchecked exception
 * each app's domain exception extends. The {@code Severity -> slf4j Level} translation lives only
 * in {@code :shared:observability}.
 */
@NullMarked
package io.github.p4suta.shared.kernel.error;

import org.jspecify.annotations.NullMarked;
