package io.github.p4suta.webapp.port;

import io.github.p4suta.webapp.domain.JobId;

/** Mints fresh, unguessable job ids. The production adapter returns random UUIDs. */
public interface JobIdGenerator {

    /** {@return a new, unique job id} */
    JobId next();
}
