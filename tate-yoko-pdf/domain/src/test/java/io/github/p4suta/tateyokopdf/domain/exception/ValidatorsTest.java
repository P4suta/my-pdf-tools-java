package io.github.p4suta.tateyokopdf.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ValidatorsTest {

    @Test
    void requireDoesNothingOnTrue() {
        assertThatNoException()
                .isThrownBy(() -> Validators.require(true, ErrorKind.INVALID_PARAMETER, "ok"));
    }

    @Test
    void requireThrowsSpreadExceptionWithDetailOnFalse() {
        assertThatThrownBy(() -> Validators.require(false, ErrorKind.PDF_INVALID_PAGE, "n<0"))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> {
                            assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_INVALID_PAGE);
                            assertThat(ex.technicalDetail()).isEqualTo("n<0");
                        });
    }

    @Test
    void requireNonNullReturnsValue() {
        String v = Validators.requireNonNull("hi", ErrorKind.INVALID_PARAMETER, "v");
        assertThat(v).isEqualTo("hi");
    }

    @Test
    void requireNonNullThrowsOnNull() {
        assertThatThrownBy(() -> Validators.requireNonNull(null, ErrorKind.INVALID_PARAMETER, "v"))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.technicalDetail()).contains("v").contains("null"));
    }

    @Test
    void requirePositiveThrowsOnZero() {
        assertThatThrownBy(() -> Validators.requirePositive(0f, ErrorKind.INVALID_PARAMETER, "x"))
                .isInstanceOf(SpreadException.class);
    }

    @Test
    void requirePositiveThrowsOnNegative() {
        assertThatThrownBy(() -> Validators.requirePositive(-1f, ErrorKind.INVALID_PARAMETER, "x"))
                .isInstanceOf(SpreadException.class);
    }

    @Test
    void requirePositiveReturnsValueOnPositive() {
        assertThat(Validators.requirePositive(1.5f, ErrorKind.INVALID_PARAMETER, "x"))
                .isEqualTo(1.5f);
    }

    @Test
    void requireNonNegativeThrowsOnNegative() {
        assertThatThrownBy(() -> Validators.requireNonNegative(-1, ErrorKind.PDF_INVALID_PAGE, "p"))
                .isInstanceOf(SpreadException.class);
    }

    @Test
    void requireNonNegativeAcceptsZero() {
        assertThat(Validators.requireNonNegative(0, ErrorKind.PDF_INVALID_PAGE, "p")).isZero();
    }

    @Test
    void requireExistsReturnsPathWhenPresent(@TempDir Path tmp) throws Exception {
        Path p = Files.createFile(tmp.resolve("a.txt"));
        assertThat(Validators.requireExists(p, ErrorKind.PDF_NOT_FOUND)).isEqualTo(p);
    }

    @Test
    void requireExistsThrowsWhenMissing(@TempDir Path tmp) {
        Path p = tmp.resolve("missing.txt");
        assertThatThrownBy(() -> Validators.requireExists(p, ErrorKind.PDF_NOT_FOUND))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> {
                            assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_NOT_FOUND);
                            assertThat(ex.technicalDetail()).contains("missing.txt");
                        });
    }
}
