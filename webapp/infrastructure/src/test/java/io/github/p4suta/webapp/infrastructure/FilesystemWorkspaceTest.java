package io.github.p4suta.webapp.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.webapp.domain.JobId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemWorkspaceTest {

    private static final JobId ID = new JobId("job-1");

    @TempDir Path tmp;
    private FilesystemWorkspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new FilesystemWorkspace(tmp);
    }

    @Test
    void storesTheUploadHashesItAndReportsTheResultOnceWritten() throws IOException {
        workspace.allocate(ID);
        String sha =
                workspace.storeUpload(
                        ID, new ByteArrayInputStream("%PDF-1.7 scan".getBytes(UTF_8)));

        assertThat(Files.readString(workspace.inputPdf(ID))).isEqualTo("%PDF-1.7 scan");
        assertThat(sha).isEqualTo(sha256Hex("%PDF-1.7 scan"));
        assertThat(workspace.resultIfPresent(ID)).isEmpty();

        Files.writeString(workspace.outputPdf(ID), "book");
        assertThat(workspace.resultIfPresent(ID)).contains(workspace.outputPdf(ID));
    }

    @Test
    void rejectsAnUploadThatIsNotAPdf() throws IOException {
        workspace.allocate(ID);
        assertThatThrownBy(
                        () ->
                                workspace.storeUpload(
                                        ID, new ByteArrayInputStream("not-a-pdf".getBytes(UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeResultExposesASourceAsTheJobsOutput() throws IOException {
        workspace.allocate(ID);
        Path source = tmp.resolve("cached.pdf");
        Files.writeString(source, "%PDF cached");

        workspace.placeResult(ID, source);

        assertThat(Files.readString(workspace.outputPdf(ID))).isEqualTo("%PDF cached");
        assertThat(workspace.resultIfPresent(ID)).contains(workspace.outputPdf(ID));
    }

    @Test
    void removeDeletesTheWholeJobDirectory() throws IOException {
        workspace.allocate(ID);
        Files.writeString(workspace.inputPdf(ID), "scan");
        Files.writeString(workspace.outputPdf(ID), "book");

        workspace.remove(ID);

        assertThat(Files.exists(workspace.inputPdf(ID).getParent())).isFalse();
    }

    @Test
    void removingAMissingJobIsANoOp() throws IOException {
        workspace.remove(new JobId("ghost"));
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
