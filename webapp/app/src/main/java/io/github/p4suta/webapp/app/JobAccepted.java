package io.github.p4suta.webapp.app;

/**
 * The response body of an accepted conversion (HTTP 202).
 *
 * @param jobId the new job's id
 * @param state the job's initial state
 */
public record JobAccepted(String jobId, String state) {}
