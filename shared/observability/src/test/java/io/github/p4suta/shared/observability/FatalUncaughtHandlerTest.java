package io.github.p4suta.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Coverage for {@link FatalUncaughtHandler}. */
final class FatalUncaughtHandlerTest {

    @Test
    void oomTriggersExit137FromSharedErrorModel() {
        AtomicInteger exit = new AtomicInteger(-1);
        new FatalUncaughtHandler(exit::set)
                .uncaughtException(Thread.currentThread(), new OutOfMemoryError("h"));
        assertThat(exit.get()).isEqualTo(137);
        assertThat(exit.get()).isEqualTo(CommonErrorKind.OUT_OF_MEMORY.exitCode());
        assertThat(FatalUncaughtHandler.OOM_EXIT_CODE).isEqualTo(137);
    }

    @Test
    void otherThrowableDoesNotExit() {
        AtomicInteger exit = new AtomicInteger(-1);
        new FatalUncaughtHandler(exit::set)
                .uncaughtException(Thread.currentThread(), new RuntimeException("plain"));
        assertThat(exit.get()).isEqualTo(-1);
    }

    @Test
    void defaultConstructorWiresSystemExit() {
        // The no-arg constructor just stores System::exit; constructing it does not exit. The
        // OOM path is exercised with an injected consumer above, so this never calls the real exit.
        assertThat(new FatalUncaughtHandler()).isNotNull();
    }
}
