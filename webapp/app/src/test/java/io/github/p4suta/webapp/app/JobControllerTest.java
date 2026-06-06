package io.github.p4suta.webapp.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.p4suta.webapp.application.Conversions;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class JobControllerTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    private final Conversions conversions = mock(Conversions.class);
    private final SseProgressPublisher publisher = mock(SseProgressPublisher.class);
    private final JobController controller = new JobController(conversions, publisher);

    private static MultipartFile pdf(String filename) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("%PDF".getBytes(UTF_8)));
        return file;
    }

    @Test
    void submitQueuesTheConversionAndReturnsTheJobId() throws IOException {
        when(conversions.submit(any(), eq("scan.pdf"), any())).thenReturn(new JobId("job-1"));

        JobAccepted accepted =
                controller.submit(
                        pdf("scan.pdf"), "RTL", "right", true, true, true, true, false, 0);

        assertThat(accepted.jobId()).isEqualTo("job-1");
        assertThat(accepted.state()).isEqualTo("QUEUED");
        ArgumentCaptor<ConversionRequest> captor = ArgumentCaptor.forClass(ConversionRequest.class);
        verify(conversions).submit(captor.capture(), eq("scan.pdf"), any());
        assertThat(captor.getValue().direction()).isEqualTo(Direction.RTL);
        assertThat(captor.getValue().firstPage()).isEqualTo(FirstPage.RIGHT);
        assertThat(captor.getValue().jobs()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void submitRejectsANonPdfUpload() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");

        assertThatThrownBy(
                        () ->
                                controller.submit(
                                        file, "RTL", "right", true, true, true, true, false, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void submitRejectsAnInvalidDirection() throws IOException {
        MultipartFile file = pdf("a.pdf");
        assertThatThrownBy(
                        () ->
                                controller.submit(
                                        file,
                                        "sideways",
                                        "right",
                                        true,
                                        true,
                                        true,
                                        true,
                                        false,
                                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void statusReportsTheJob() {
        when(conversions.get(new JobId("job-1")))
                .thenReturn(Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH));

        JobStatusResponse status = controller.status("job-1");

        assertThat(status.jobId()).isEqualTo("job-1");
        assertThat(status.state()).isEqualTo("QUEUED");
    }

    @Test
    void resultServesThePdfInlineWithItsBookFilename() {
        Job done =
                Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH)
                        .toRunning()
                        .toDone(Instant.EPOCH);
        when(conversions.get(new JobId("job-1"))).thenReturn(done);
        when(conversions.result(new JobId("job-1"))).thenReturn(Path.of("/tmp/out.pdf"));

        ResponseEntity<Resource> response = controller.result("job-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        // inline so the browser previews the book in a new tab; the suggested filename is kept.
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline")
                .contains("scan_book.pdf");
    }

    @Test
    void eventsOpensTheJobsStream() {
        Job job = Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH);
        SseEmitter stream = new SseEmitter();
        when(conversions.get(new JobId("job-1"))).thenReturn(job);
        when(publisher.openStream(job)).thenReturn(stream);

        assertThat(controller.events("job-1")).isSameAs(stream);
    }

    @Test
    void rejectsAnUnsafeJobIdInThePath() {
        assertThatThrownBy(() -> controller.status("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe token");
    }
}
