package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobNotFoundExceptionTest {

    @Test
    void messageNamesTheMissingId() {
        assertThat(new JobNotFoundException(new JobId("missing")))
                .hasMessage("no such job: missing");
    }
}
