package io.github.p4suta.webapp.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.p4suta.webapp.application.Conversions;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobNotFoundException;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.domain.ResultNotReadyException;
import io.github.p4suta.webapp.port.QueueFullException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * HTTP-layer slice tests: these drive the real Spring web stack — request mapping, multipart
 * binding, {@code @RequestParam} defaults and coercion, the {@code @RestControllerAdvice}, content
 * negotiation, and JSON serialization — which the direct-call {@link JobControllerTest} cannot
 * reach. The framework-free use cases are mocked, so only the web edge is under test.
 */
@WebMvcTest(JobController.class)
@SuppressWarnings("NullAway.Init") // mvc / conversions / publisher are framework-injected
class JobControllerWebMvcTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);
    private static final String SHA = "a".repeat(64);

    @Autowired private MockMvcTester mvc;
    @MockitoBean private Conversions conversions;
    @MockitoBean private SseProgressPublisher publisher;

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF".getBytes(UTF_8));
    }

    @Test
    void submitAcceptsTheUploadAndAppliesRequestParamDefaults() throws Exception {
        when(conversions.submit(any(), eq("scan.pdf"), any())).thenReturn(new JobId("job-1"));

        assertThat(mvc.post().uri("/api/v1/jobs").multipart().file(pdf()).exchange())
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.jobId")
                .isEqualTo("job-1");

        ArgumentCaptor<ConversionRequest> captor = ArgumentCaptor.forClass(ConversionRequest.class);
        verify(conversions).submit(captor.capture(), eq("scan.pdf"), any());
        ConversionRequest request = captor.getValue();
        assertThat(request.direction()).isEqualTo(Direction.RTL);
        assertThat(request.firstPage()).isEqualTo(FirstPage.RIGHT);
        assertThat(request.despeckle()).isTrue();
        assertThat(request.register()).isTrue();
        assertThat(request.pdfA()).isFalse();
        assertThat(request.jobs()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void submitBindsExplicitOptions() throws Exception {
        when(conversions.submit(any(), any(), any())).thenReturn(new JobId("job-2"));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/jobs")
                                .multipart()
                                .file(pdf())
                                .param("direction", "LTR")
                                .param("firstPage", "cover")
                                .param("pdfA", "true")
                                .param("jobs", "3")
                                .exchange())
                .hasStatus(HttpStatus.ACCEPTED);

        ArgumentCaptor<ConversionRequest> captor = ArgumentCaptor.forClass(ConversionRequest.class);
        verify(conversions).submit(captor.capture(), any(), any());
        ConversionRequest request = captor.getValue();
        assertThat(request.direction()).isEqualTo(Direction.LTR);
        assertThat(request.firstPage()).isEqualTo(FirstPage.COVER);
        assertThat(request.pdfA()).isTrue();
        assertThat(request.jobs()).isEqualTo(3);
    }

    @Test
    void submitWithoutAFileIsABadRequest() {
        assertThat(mvc.post().uri("/api/v1/jobs").multipart().exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitRejectsAnInvalidOptionAsAProblem() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/jobs")
                                .multipart()
                                .file(pdf())
                                .param("direction", "sideways")
                                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("bad_request");
    }

    @Test
    void statusReturnsTheJobAsJson() {
        when(conversions.get(new JobId("job-1")))
                .thenReturn(Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH));

        assertThat(mvc.get().uri("/api/v1/jobs/{id}", "job-1").exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.state")
                .isEqualTo("QUEUED");
    }

    @Test
    void failedStatusExposesTheKindButNotTheServerOnlyMessage() {
        Job failed =
                Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH)
                        .toRunning()
                        .toFailed(Instant.EPOCH, "INTERNAL", "qpdf failed: /tmp/secret/path boom");
        when(conversions.get(new JobId("job-1"))).thenReturn(failed);

        var response = mvc.get().uri("/api/v1/jobs/{id}", "job-1").exchange();
        assertThat(response)
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.errorKind")
                .isEqualTo("INTERNAL");
        // The server-only failure detail must never reach the client.
        assertThat(response).bodyText().doesNotContain("secret", "qpdf", "errorMessage");
    }

    @Test
    void resultServesThePdfInlineWithTheBookFilename(@TempDir Path tmp) throws Exception {
        Path out = Files.writeString(tmp.resolve("out.pdf"), "%PDF-1.7");
        Job done =
                Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH)
                        .toRunning()
                        .toDone(Instant.EPOCH);
        when(conversions.get(new JobId("job-1"))).thenReturn(done);
        when(conversions.result(new JobId("job-1"))).thenReturn(out);

        assertThat(mvc.get().uri("/api/v1/jobs/{id}/result", "job-1").exchange())
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_PDF)
                .hasHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"scan_book.pdf\"");
    }

    @Test
    void unknownJobMapsToNotFoundProblem() {
        when(conversions.get(any())).thenThrow(new JobNotFoundException(new JobId("nope")));

        assertThat(mvc.get().uri("/api/v1/jobs/{id}", "nope").exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_found");
    }

    @Test
    void resultBeforeDoneMapsToConflictProblem() {
        Job queued = Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH);
        when(conversions.get(new JobId("job-1"))).thenReturn(queued);
        when(conversions.result(new JobId("job-1")))
                .thenThrow(new ResultNotReadyException(new JobId("job-1"), JobState.QUEUED));

        assertThat(mvc.get().uri("/api/v1/jobs/{id}/result", "job-1").exchange())
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_ready");
    }

    @Test
    void aFullQueueMapsToTooManyRequestsProblem() throws Exception {
        when(conversions.submit(any(), any(), any()))
                .thenThrow(new QueueFullException("conversion queue is full"));

        assertThat(mvc.post().uri("/api/v1/jobs").multipart().file(pdf()).exchange())
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("busy");
    }

    @Test
    void eventsOpensAnEventStream() {
        Job job = Job.queued(new JobId("job-1"), REQUEST, "scan.pdf", Instant.EPOCH);
        SseEmitter emitter = new SseEmitter();
        emitter.complete(); // completed up front so the async exchange returns at once (no 1h wait)
        when(conversions.get(new JobId("job-1"))).thenReturn(job);
        when(publisher.openStream(job)).thenReturn(emitter);

        assertThat(mvc.get().uri("/api/v1/jobs/{id}/events", "job-1").exchange())
                .hasStatus(HttpStatus.OK)
                .hasContentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void probeReturnsAReadyJobOnACacheHit() throws Exception {
        when(conversions.probe(eq(SHA), any(), eq("scan.pdf")))
                .thenReturn(Optional.of(new JobId("job-9")));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/jobs/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(probeBody(SHA))
                                .exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.jobId")
                .isEqualTo("job-9");
    }

    @Test
    void probeReturnsNoContentOnACacheMiss() throws Exception {
        when(conversions.probe(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(
                        mvc.post()
                                .uri("/api/v1/jobs/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(probeBody(SHA))
                                .exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void probeRejectsAMalformedSha() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/jobs/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(probeBody("nope"))
                                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("bad_request");
    }

    private static String probeBody(String sha) {
        return """
        {"sha256":"%s","direction":"RTL","firstPage":"right","despeckle":true,\
        "register":true,"deskew":true,"scale":true,"pdfA":false,"originalFilename":"scan.pdf"}\
        """
                .formatted(sha);
    }
}
