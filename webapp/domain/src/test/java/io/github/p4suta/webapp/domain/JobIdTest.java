package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobIdTest {

    @Test
    void exposesItsValue() {
        assertThat(new JobId("abc-123").value()).isEqualTo("abc-123");
    }

    @Test
    void acceptsAUuid() {
        String uuid = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
        assertThat(new JobId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new JobId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("job id must not be blank");
    }

    @Test
    void rejectsPathTraversalAndUnsafeCharacters() {
        assertThatThrownBy(() -> new JobId("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe token");
        assertThatThrownBy(() -> new JobId("a/b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe token");
    }
}
