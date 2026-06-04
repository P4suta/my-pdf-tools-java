package io.github.p4suta.shared.observability;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Thread.UncaughtExceptionHandler} for the whole JVM. Ensures any thread that dies
 * with an unhandled throwable still emits an ERROR log line (rather than vanishing into a bare
 * stderr stack trace) and, on {@link OutOfMemoryError}, exits {@code 137} via the injected {@code
 * exit} consumer so the process terminates with a meaningful code instead of limping on in a
 * corrupted state. The entry point installs this via {@link
 * Thread#setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)}.
 *
 * <p>The OOM exit code is sourced from {@link CommonErrorKind#OUT_OF_MEMORY} so it stays in
 * lockstep with the shared error model rather than a duplicated local constant. Generalized from
 * tate-yoko-pdf's handler (the deduplicated register/despeckle shape).
 */
public final class FatalUncaughtHandler implements Thread.UncaughtExceptionHandler {

    /** Exit code on out-of-memory, sourced from the shared error model ({@code 137}). */
    public static final int OOM_EXIT_CODE = CommonErrorKind.OUT_OF_MEMORY.exitCode();

    private static final Logger LOG = LoggerFactory.getLogger(FatalUncaughtHandler.class);

    private final IntConsumer exit;

    /** Creates a handler that calls {@link System#exit(int)} on out-of-memory. */
    public FatalUncaughtHandler() {
        this(System::exit);
    }

    /**
     * Creates a handler that calls {@code exit} on out-of-memory (seam for tests).
     *
     * @param exit invoked with {@link #OOM_EXIT_CODE} when an {@link OutOfMemoryError} is seen
     */
    public FatalUncaughtHandler(IntConsumer exit) {
        this.exit = exit;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (e instanceof OutOfMemoryError) {
            LOG.error("OOM on thread {} — exiting", t.getName(), e);
            exit.accept(OOM_EXIT_CODE);
            return;
        }
        LOG.error("Uncaught exception on thread {}", t.getName(), e);
    }
}
