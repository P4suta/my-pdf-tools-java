package io.github.p4suta.webapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.JobId;
import org.junit.jupiter.api.Test;

class UuidJobIdGeneratorTest {

    @Test
    void mintsDistinctSafeTokenIds() {
        UuidJobIdGenerator generator = new UuidJobIdGenerator();

        JobId first = generator.next();
        JobId second = generator.next();

        assertThat(first).isNotEqualTo(second);
        assertThat(first.value()).matches("[A-Za-z0-9-]+");
    }
}
