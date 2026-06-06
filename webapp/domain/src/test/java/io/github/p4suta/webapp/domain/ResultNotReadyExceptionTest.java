package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResultNotReadyExceptionTest {

    @Test
    void messageNamesTheIdAndState() {
        assertThat(new ResultNotReadyException(new JobId("j1"), JobState.RUNNING))
                .hasMessage("job j1 is RUNNING, not yet DONE");
    }
}
