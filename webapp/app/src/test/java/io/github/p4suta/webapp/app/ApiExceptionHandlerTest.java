package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.QueueFullException;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsJobNotFoundTo404Problem() {
        ProblemDetail problem = handler.notFound(new JobNotFoundException(new JobId("x")));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(code(problem)).isEqualTo("not_found");
    }

    @Test
    void mapsResultNotReadyTo409Problem() {
        ProblemDetail problem =
                handler.notReady(new ResultNotReadyException(new JobId("x"), JobState.RUNNING));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(code(problem)).isEqualTo("not_ready");
    }

    @Test
    void mapsQueueFullTo429Problem() {
        ProblemDetail problem = handler.queueFull(new QueueFullException("full"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(code(problem)).isEqualTo("busy");
    }

    @Test
    void mapsIllegalArgumentTo400Problem() {
        ProblemDetail problem = handler.badRequest(new IllegalArgumentException("bad"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(code(problem)).isEqualTo("bad_request");
    }

    @Test
    void mapsMaxUploadSizeTo413Problem() {
        ProblemDetail problem = handler.tooLarge(new MaxUploadSizeExceededException(512));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(code(problem)).isEqualTo("too_large");
    }

    @Test
    void mapsAnIoErrorToAGeneric500WithoutLeakingDetail() {
        ProblemDetail problem = handler.internal(new IOException("secret internal path"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(code(problem)).isEqualTo("internal");
        assertThat(problem.getDetail()).doesNotContain("secret");
    }

    private static @Nullable Object code(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties == null ? null : properties.get("code");
    }
}
