package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.JobIdGenerator;
import java.util.UUID;

/** Mints {@link JobId}s from random UUIDs — unguessable and a safe filesystem/URL token. */
public final class UuidJobIdGenerator implements JobIdGenerator {

    @Override
    public JobId next() {
        return new JobId(UUID.randomUUID().toString());
    }
}
