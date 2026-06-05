package io.github.p4suta.pipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CorpusTest {

    @Test
    void exposesComponents() {
        Corpus c = new Corpus(Path.of("/work/in"), "*.tif", 600, 87);
        assertThat(c.dir()).isEqualTo(Path.of("/work/in"));
        assertThat(c.glob()).isEqualTo("*.tif");
        assertThat(c.dpi()).isEqualTo(600);
        assertThat(c.pageCount()).isEqualTo(87);
    }

    @Test
    void movedToChangesDirAndGlobButKeepsDpiAndCount() {
        Corpus moved =
                new Corpus(Path.of("/work/in"), "*.tif", 600, 87)
                        .movedTo(Path.of("/work/reg"), "*.tiff");
        assertThat(moved.dir()).isEqualTo(Path.of("/work/reg"));
        assertThat(moved.glob()).isEqualTo("*.tiff");
        assertThat(moved.dpi()).isEqualTo(600);
        assertThat(moved.pageCount()).isEqualTo(87);
    }

    @Test
    void hasValueSemantics() {
        Corpus a = new Corpus(Path.of("/x"), "*.tif", 600, 5);
        Corpus b = new Corpus(Path.of("/x"), "*.tif", 600, 5);
        Corpus other = new Corpus(Path.of("/y"), "*.tif", 600, 5);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(other);
        assertThat(a.toString()).contains("*.tif");
    }

    @Test
    void rejectsBlankGlob() {
        assertThatThrownBy(() -> new Corpus(Path.of("/x"), " ", 600, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("glob");
    }

    @Test
    void rejectsNonPositiveDpi() {
        assertThatThrownBy(() -> new Corpus(Path.of("/x"), "*.tif", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dpi");
    }

    @Test
    void rejectsNegativePageCount() {
        assertThatThrownBy(() -> new Corpus(Path.of("/x"), "*.tif", 600, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageCount");
    }
}
