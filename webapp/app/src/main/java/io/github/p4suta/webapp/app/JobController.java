package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.application.Conversions;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The pdfbook web API. Thin: it parses and validates the request, hands the work to the
 * framework-free {@link Conversions} use cases, and shapes the response — all business rules live
 * below. Failures surface as the domain's typed exceptions, which {@link ApiExceptionHandler} maps
 * to status codes.
 */
@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final Conversions conversions;
    private final SseProgressPublisher publisher;
    private final int defaultJobs;

    /**
     * @param conversions the conversion use cases
     * @param publisher the SSE progress fan-out
     */
    public JobController(Conversions conversions, SseProgressPublisher publisher) {
        this.conversions = conversions;
        this.publisher = publisher;
        this.defaultJobs = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Accepts an upload and queues a conversion.
     *
     * @param file the scan PDF
     * @param direction RTL or LTR
     * @param firstPage right, left, or cover
     * @param despeckle whether to run the dust-removal stage
     * @param register whether to run the deskew/alignment stage
     * @param deskew whether to straighten pages within the register stage
     * @param scale whether to scale columns within the register stage
     * @param pdfA whether to emit PDF/A-2b conformance
     * @param jobs worker threads ({@code 0} means use the server default)
     * @return the accepted job
     * @throws IOException if the upload cannot be stored
     */
    @PostMapping(path = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "RTL") String direction,
            @RequestParam(defaultValue = "right") String firstPage,
            @RequestParam(defaultValue = "true") boolean despeckle,
            @RequestParam(defaultValue = "true") boolean register,
            @RequestParam(defaultValue = "true") boolean deskew,
            @RequestParam(defaultValue = "true") boolean scale,
            @RequestParam(defaultValue = "false") boolean pdfA,
            @RequestParam(defaultValue = "0") int jobs)
            throws IOException {
        requirePdf(file);
        ConversionRequest request =
                new ConversionRequest(
                        direction(direction),
                        firstPage(firstPage),
                        despeckle,
                        register,
                        deskew,
                        scale,
                        pdfA,
                        jobs > 0 ? jobs : defaultJobs);
        String filename = sanitizeFilename(file.getOriginalFilename());
        try (InputStream upload = file.getInputStream()) {
            JobId id = conversions.submit(request, filename, upload);
            return new JobAccepted(id.value(), "QUEUED");
        }
    }

    /**
     * {@return the status of job {@code id}}
     *
     * @param id the job id
     */
    @GetMapping("/jobs/{id}")
    public JobStatusResponse status(@PathVariable String id) {
        return JobStatusResponse.from(conversions.get(new JobId(id)));
    }

    /**
     * Opens a Server-Sent Events stream of job {@code id}'s progress (replaying anything already
     * emitted).
     *
     * @param id the job id
     * @return the SSE stream
     */
    @GetMapping("/jobs/{id}/events")
    public SseEmitter events(@PathVariable String id) {
        Job job = conversions.get(new JobId(id));
        return publisher.openStream(job);
    }

    /**
     * Downloads job {@code id}'s finished book PDF. The optional trailing {@code filename} segment
     * is ignored — it lets the SPA put the book's name at the end of the URL so a browser
     * previewing the {@code inline} PDF downloads it as {@code <book>_book.pdf} rather than a
     * generic name.
     *
     * @param id the job id
     * @return the PDF, served inline with the book filename in the Content-Disposition
     */
    @GetMapping({"/jobs/{id}/result", "/jobs/{id}/result/{filename}"})
    public ResponseEntity<Resource> result(@PathVariable String id) {
        JobId jobId = new JobId(id);
        Job job = conversions.get(jobId);
        Path result = conversions.result(jobId);
        return ResponseEntity.ok()
                // inline (not attachment) so a browser opens the book in a new tab to preview it;
                // the viewer's own Save still uses the suggested filename.
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + downloadName(job.originalFilename()) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(result));
    }

    private static void requirePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("the uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType != null && !MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                    "only application/pdf uploads are accepted, got " + contentType);
        }
    }

    private static Direction direction(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "RTL" -> Direction.RTL;
            case "LTR" -> Direction.LTR;
            default ->
                    throw new IllegalArgumentException(
                            "invalid direction '" + value + "' (RTL or LTR)");
        };
    }

    private static FirstPage firstPage(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "right" -> FirstPage.RIGHT;
            case "left" -> FirstPage.LEFT;
            case "cover" -> FirstPage.COVER;
            default ->
                    throw new IllegalArgumentException(
                            "invalid first-page '" + value + "' (right, left, or cover)");
        };
    }

    private static String sanitizeFilename(@Nullable String original) {
        if (original == null || original.isBlank()) {
            return "upload.pdf";
        }
        Path name = Path.of(original).getFileName();
        return name == null ? "upload.pdf" : name.toString();
    }

    private static String downloadName(String originalFilename) {
        String base = originalFilename.replaceFirst("(?i)\\.pdf$", "");
        return (base.isBlank() ? "book" : base) + "_book.pdf";
    }
}
