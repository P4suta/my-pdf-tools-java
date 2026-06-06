package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.QueueFullException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the domain's typed failures to HTTP status codes with a uniform {@link ApiError} body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * {@return a 404 body} for an unknown job.
     *
     * @param exception the cause
     */
    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(JobNotFoundException exception) {
        return new ApiError("not_found", exception.getMessage());
    }

    /**
     * {@return a 409 body} when a result is requested before the job is done.
     *
     * @param exception the cause
     */
    @ExceptionHandler(ResultNotReadyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError notReady(ResultNotReadyException exception) {
        return new ApiError("not_ready", exception.getMessage());
    }

    /**
     * {@return a 429 body} when the conversion queue is full.
     *
     * @param exception the cause
     */
    @ExceptionHandler(QueueFullException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiError queueFull(QueueFullException exception) {
        return new ApiError("busy", exception.getMessage());
    }

    /**
     * {@return a 400 body} for an invalid request (bad option, unsafe id, wrong content type).
     *
     * @param exception the cause
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException exception) {
        return new ApiError("bad_request", exception.getMessage());
    }
}
