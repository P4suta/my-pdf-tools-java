package io.github.p4suta.webapp.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.JobId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void storesTheUploadAndReportsTheResultOnceWritten() throws IOException {
        workspace.allocate(ID);
        workspace.storeUpload(ID, new ByteArrayInputStream("scan".getBytes(UTF_8)));

        assertThat(Files.readString(workspace.inputPdf(ID))).isEqualTo("scan");
        assertThat(workspace.resultIfPresent(ID)).isEmpty();

        Files.writeString(workspace.outputPdf(ID), "book");
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
}
