package io.github.p4suta.register.domain.model;

import io.github.p4suta.shared.kernel.Validators;

/** The fixed output page in pixels: a paper size rasterised at a chosen resolution. */
public record Canvas(int width, int height) {

    public Canvas {
        Validators.requirePositive(width, "width");
        Validators.requirePositive(height, "height");
    }

    /** The pixel canvas for {@code paper} at {@code dpi}. */
    public static Canvas of(PaperSize paper, int dpi) {
        return new Canvas(paper.widthPx(dpi), paper.heightPx(dpi));
    }
}
