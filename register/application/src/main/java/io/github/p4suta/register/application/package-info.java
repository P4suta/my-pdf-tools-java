/**
 * Orchestration: the corpus registration service ({@link
 * io.github.p4suta.register.application.RegistrationService} — the two-pass directory walk and
 * worker pool) and the PDF -> PDF drivers ({@link
 * io.github.p4suta.register.application.PdfPipelineService} and {@link
 * io.github.p4suta.register.application.PdfBatchService}). Depends only on {@code :domain} and
 * {@code :port}; the Leptonica, PDFBox and pdfimages/jbig2 adapters are injected at the {@code
 * :app} composition root, so this layer never sees {@code :infrastructure}.
 */
@NullMarked
package io.github.p4suta.register.application;

import org.jspecify.annotations.NullMarked;
