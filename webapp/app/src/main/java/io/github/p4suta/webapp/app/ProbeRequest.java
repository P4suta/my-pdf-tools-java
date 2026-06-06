package io.github.p4suta.webapp.app;

import org.jspecify.annotations.Nullable;

/**
 * The JSON body of a cache probe ({@code POST /api/v1/jobs/probe}): the client-computed SHA-256 of
 * the PDF it is about to upload, plus the same conversion options it would submit. On a cache hit
 * the server mints a ready job, so the client skips uploading the file. The worker-thread count is
 * absent: it does not affect the cache key.
 *
 * @param sha256 the lowercase-hex SHA-256 of the PDF bytes
 * @param direction {@code RTL} or {@code LTR}
 * @param firstPage {@code right}, {@code left}, or {@code cover}
 * @param despeckle whether the dust-removal stage would run
 * @param register whether the deskew/alignment stage would run
 * @param deskew whether pages would be straightened within the register stage
 * @param scale whether columns would be scaled within the register stage
 * @param pdfA whether PDF/A-2b conformance would be emitted
 * @param originalFilename the upload's name, used to name the download (may be {@code null})
 */
public record ProbeRequest(
        @Nullable String sha256,
        @Nullable String direction,
        @Nullable String firstPage,
        boolean despeckle,
        boolean register,
        boolean deskew,
        boolean scale,
        boolean pdfA,
        @Nullable String originalFilename) {}
