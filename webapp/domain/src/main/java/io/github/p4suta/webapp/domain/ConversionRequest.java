package io.github.p4suta.webapp.domain;

import io.github.p4suta.shared.kernel.Validators;

/**
 * The validated options for one pdfbook conversion. A web-owned value type with its own {@link
 * Direction} / {@link FirstPage} enums, so the web feature stays decoupled from the pipeline's
 * internals; the infrastructure adapter maps it to pdfbook command-line flags.
 *
 * @param direction reading direction of the spreads
 * @param firstPage which side page one opens on
 * @param despeckle whether to run the dust-removal stage
 * @param register whether to run the deskew/alignment stage
 * @param deskew within the register stage, whether to straighten each page
 * @param scale within the register stage, whether to scale columns to the reference height
 * @param pdfA whether to emit PDF/A-2b conformance
 * @param jobs worker threads for the conversion (at least one)
 */
public record ConversionRequest(
        Direction direction,
        FirstPage firstPage,
        boolean despeckle,
        boolean register,
        boolean deskew,
        boolean scale,
        boolean pdfA,
        int jobs) {

    public ConversionRequest {
        Validators.requireNonNull(direction, "direction");
        Validators.requireNonNull(firstPage, "firstPage");
        Validators.requirePositive(jobs, "jobs");
    }
}
