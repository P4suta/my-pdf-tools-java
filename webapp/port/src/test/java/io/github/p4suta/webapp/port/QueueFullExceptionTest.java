package io.github.p4suta.webapp.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueueFullExceptionTest {

    @Test
    void carriesItsMessage() {
        assertThat(new QueueFullException("queue is full (max 1)"))
                .hasMessage("queue is full (max 1)");
    }
}
