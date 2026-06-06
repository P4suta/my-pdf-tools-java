package io.github.p4suta.tateyokopdf.domain;

import io.github.p4suta.tateyokopdf.domain.model.LayoutPosition;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Pins the equals/hashCode/toString contracts for the domain records, so a later manual override or
 * value-vs-entity mistake is caught by EqualsVerifier.
 *
 * <p>Records with validating compact constructors ({@code PageDimension}, {@code SpreadSpec},
 * {@code SpreadLayout}, {@code PagePairSpec.*}) are excluded: EqualsVerifier probes with edge-case
 * values like {@code 0.0f}, which the production {@code Validators.requirePositive}/{@code
 * requireNonNegative} preconditions reject.
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
