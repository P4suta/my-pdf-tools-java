package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class MemoryModeTest {

    @Test
    void exposesInMemoryAndScratchFileModes() {
        assertThat(MemoryMode.values())
                .containsExactly(MemoryMode.IN_MEMORY, MemoryMode.SCRATCH_FILE);
        assertThat(MemoryMode.valueOf("SCRATCH_FILE")).isEqualTo(MemoryMode.SCRATCH_FILE);
    }
}
