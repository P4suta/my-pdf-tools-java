package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.QueueFullException;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps the domain's typed failures — plus an over-size upload and an unexpected I/O error — to RFC
 * 9457 {@link ProblemDetail} responses (media type {@code application/problem+json}). Each problem
 * carries a stable, machine-readable {@code code} property so a client can branch on the failure
 * kind without parsing the human-readable {@code detail}.
 *
 * <p>Spring MVC's own framework exceptions (415, 405, malformed body, …) are already rendered as
 * problem+json by {@code spring.mvc.problemdetails.enabled}; this advice deliberately does NOT add
 * a broad {@code Exception} handler, which would intercept and flatten those. Anything not handled
 * here falls to the container's default error path, which the {@code prod} profile strips of
 * messages and stack traces ({@code server.error.include-*: never}) so nothing internal leaks.
 *
 * <p>The HTTP status comes solely from the returned {@link ProblemDetail} (Spring applies {@code
 * ProblemDetail.getStatus()} to the response) — no {@code @ResponseStatus}, so there is one source
 * of truth for each status.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * {@return a 404 problem} for an unknown job.
     *
     * @param exception the cause
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail notFound(JobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Job not found", "not_found", exception.getMessage());
    }

    /**
     * {@return a 409 problem} when a result is requested before the job is done.
     *
     * @param exception the cause
     */
    @ExceptionHandler(ResultNotReadyException.class)
    public ProblemDetail notReady(ResultNotReadyException exception) {
        return problem(
                HttpStatus.CONFLICT, "Result not ready", "not_ready", exception.getMessage());
    }

    /**
     * {@return a 429 problem} when the conversion queue is full.
     *
     * @param exception the cause
     */
    @ExceptionHandler(QueueFullException.class)
    public ProblemDetail queueFull(QueueFullException exception) {
        return problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Conversion queue full",
                "busy",
                exception.getMessage());
    }

    /**
     * {@return a 400 problem} for an invalid request (bad option, unsafe id, wrong content type).
     *
     * @param exception the cause
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(
                HttpStatus.BAD_REQUEST, "Invalid request", "bad_request", exception.getMessage());
    }

    /**
     * {@return a 413 problem} when an upload exceeds the configured multipart limit.
     *
     * @param exception the cause
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException exception) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "Upload too large",
                "too_large",
                "the uploaded file exceeds the maximum allowed size");
    }

    /**
     * {@return a generic 500 problem} for an unexpected I/O failure (e.g. the upload could not be
     * stored). The cause is logged in full server-side; the response carries no internal detail.
     *
     * @param exception the cause
     */
    @ExceptionHandler(IOException.class)
    public ProblemDetail internal(IOException exception) {
        log.warn("request failed with an I/O error", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "internal",
                "the server could not complete the request");
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, String code, @Nullable String detail) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
