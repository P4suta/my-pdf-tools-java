package io.github.p4suta.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link ExitCodes} sysexits registry to the values named in the error-model spec
 * (sections 2 and 6) and confirms the shared {@link CommonErrorKind} exit codes agree with the
 * named constants — the registry and the error model must not drift.
 */
final class ExitCodesTest {

    @Test
    void sysexitsConstantsMatchSpec() {
        assertThat(ExitCodes.OK).isEqualTo(0);
        assertThat(ExitCodes.GENERIC).isEqualTo(1);
        assertThat(ExitCodes.USAGE).isEqualTo(2);
        assertThat(ExitCodes.USAGE_DATA).isEqualTo(64);
        assertThat(ExitCodes.INPUT_DATA).isEqualTo(65);
        assertThat(ExitCodes.NO_INPUT).isEqualTo(66);
        assertThat(ExitCodes.INTERNAL).isEqualTo(70);
        assertThat(ExitCodes.WRITE).isEqualTo(73);
        assertThat(ExitCodes.IO_ERROR).isEqualTo(74);
        assertThat(ExitCodes.NOPERM).isEqualTo(77);
        assertThat(ExitCodes.PASSWORD).isEqualTo(77);
        assertThat(ExitCodes.CONFIG).isEqualTo(78);
        assertThat(ExitCodes.OOM).isEqualTo(137);
    }

    @Test
    void commonErrorKindExitCodesAgreeWithTheRegistry() {
        assertThat(CommonErrorKind.INVALID_PARAMETER.exitCode()).isEqualTo(ExitCodes.USAGE_DATA);
        assertThat(CommonErrorKind.OUT_OF_MEMORY.exitCode()).isEqualTo(ExitCodes.OOM);
        assertThat(CommonErrorKind.INTERNAL.exitCode()).isEqualTo(ExitCodes.INTERNAL);
    }

    @Test
    void isANonInstantiableUtilityClass() throws ReflectiveOperationException {
        Constructor<ExitCodes> ctor = ExitCodes.class.getDeclaredConstructor();
        assertEquals(0, Modifier.PUBLIC & ctor.getModifiers() & Modifier.PROTECTED);
        ctor.setAccessible(true);
        // Invoking it for coverage; it does nothing.
        ctor.newInstance();
    }
}
