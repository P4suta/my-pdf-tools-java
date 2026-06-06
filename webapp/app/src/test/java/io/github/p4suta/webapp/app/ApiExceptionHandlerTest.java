package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.QueueFullException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsJobNotFoundTo404Body() {
        assertThat(handler.notFound(new JobNotFoundException(new JobId("x"))).error())
                .isEqualTo("not_found");
    }

    @Test
    void mapsResultNotReadyTo409Body() {
        assertThat(
                        handler.notReady(
                                        new ResultNotReadyException(
                                                new JobId("x"), JobState.RUNNING))
                                .error())
                .isEqualTo("not_ready");
    }

    @Test
    void mapsQueueFullTo429Body() {
        assertThat(handler.queueFull(new QueueFullException("full")).error()).isEqualTo("busy");
    }

    @Test
    void mapsIllegalArgumentTo400Body() {
        assertThat(handler.badRequest(new IllegalArgumentException("bad")).error())
                .isEqualTo("bad_request");
    }
}
