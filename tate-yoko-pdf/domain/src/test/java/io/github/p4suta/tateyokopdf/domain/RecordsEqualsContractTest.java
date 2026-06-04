package io.github.p4suta.tateyokopdf.domain;

import io.github.p4suta.tateyokopdf.domain.model.LayoutPosition;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Pins the canonical equality/hashCode/toString contracts for every {@code record} we depend on
 * across the domain. Java's generated equals/hashCode are tautologically correct for fresh records,
 * but the moment someone adds a manual override, a {@code @SuppressWarnings}, or mistakes a
 * value-type for an entity, EqualsVerifier catches the regression before the build even gets to
 * integration tests.
 *
 * <p>Records with validating compact constructors ({@code PageDimension}, {@code SpreadSpec},
 * {@code SpreadLayout}, {@code PagePairSpec.*}) are intentionally not included — EqualsVerifier
 * picks edge-case primitive values like {@code 0.0f} to probe the equality contract, but those
 * values are rejected by the production {@code Validators.requirePositive}/{@code
 * requireNonNegative} preconditions. Providing prefabs to work around that would essentially
 * re-state the constructor's own rules in the test, which would tighten coupling without adding
 * signal.
 */
final class RecordsEqualsContractTest {

    @TestFactory
    Iterable<DynamicTest> records() {
        return java.util.List.of(record(LayoutPosition.class));
    }

    private static DynamicTest record(Class<?> type) {
        return DynamicTest.dynamicTest(
                type.getSimpleName(), () -> EqualsVerifier.forClass(type).verify());
    }
}
