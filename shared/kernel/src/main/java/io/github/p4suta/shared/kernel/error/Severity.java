package io.github.p4suta.shared.kernel.error;

/**
 * The log severity an {@link ErrorCategory} carries, independent of any logging framework. A kernel
 * enum (NOT slf4j {@code org.slf4j.event.Level}) so {@code :shared:kernel} stays dependency-free;
 * {@code :shared:observability} owns the single {@code Severity -> Level} translation.
 *
 * <p>By the section&nbsp;1.3 invariant of the error-model spec, {@link #WARN} pairs with {@code
 * isClientFault() == true} (bad input/usage) and {@link #ERROR} with {@code isClientFault() ==
 * false} (internal/environmental). {@link #INFO} is reserved; no current kind uses it.
 */
public enum Severity {
    /** Informational; reserved, no current {@link ErrorCategory} uses it. */
    INFO,
    /** A client-fault failure (bad input/usage): logged at WARN, not alarming. */
    WARN,
    /** An internal/environmental failure: logged at ERROR. */
    ERROR
}
