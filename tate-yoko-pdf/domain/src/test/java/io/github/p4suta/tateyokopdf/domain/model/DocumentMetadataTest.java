package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DocumentMetadataTest {

    @Test
    void emptyHasEveryFieldAbsent() {
        DocumentMetadata m = DocumentMetadata.empty();
        assertThat(m.title()).isEmpty();
        assertThat(m.author()).isEmpty();
        assertThat(m.subject()).isEmpty();
        assertThat(m.keywords()).isEmpty();
        assertThat(m.creator()).isEmpty();
        assertThat(m.creationDate()).isEmpty();
        assertThat(m.language()).isEmpty();
    }

    @Test
    void emptyIsCached() {
        assertThat(DocumentMetadata.empty()).isSameAs(DocumentMetadata.empty());
    }

    @Test
    void holdsAllProvidedFields() {
        Instant created = Instant.parse("2024-04-01T12:00:00Z");
        DocumentMetadata m =
                new DocumentMetadata(
                        Optional.of("題名"),
                        Optional.of("著者"),
                        Optional.of("概要"),
                        Optional.of("k1, k2"),
                        Optional.of("作成ツール"),
                        Optional.of(created),
                        Optional.of("ja-JP"));
        assertThat(m.title()).contains("題名");
        assertThat(m.author()).contains("著者");
        assertThat(m.subject()).contains("概要");
        assertThat(m.keywords()).contains("k1, k2");
        assertThat(m.creator()).contains("作成ツール");
        assertThat(m.creationDate()).contains(created);
        assertThat(m.language()).contains("ja-JP");
    }
}
