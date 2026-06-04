package io.github.p4suta.register.infrastructure.leptonica;

import io.github.p4suta.register.domain.model.OutputFormat;

/**
 * Maps the domain {@link OutputFormat} to the Leptonica {@code IFF_*} code {@code pixWrite}
 * expects. This is the one register-side place that knows the binding's format constants, so {@code
 * OutputFormat} carries no Leptonica coupling and {@code Pix.write(Path, int)} can stay a primitive
 * that takes a raw {@code IFF_*} code.
 *
 * <p>The {@code IFF_*} values are the literal Leptonica constants from {@code imageio.h} (verified
 * against Leptonica 1.82.0). They are duplicated here — rather than read from the shared {@code
 * io.github.p4suta.shared.imaging.Leptonica} island — because that binding keeps its constants
 * package-private (the FFM confinement that pins the unsafe surface), so they are not visible
 * across the package boundary. They must be re-confirmed if the pinned Leptonica version ever
 * changes.
 */
public final class LeptonicaFormats {

    private LeptonicaFormats() {}

    /** PNG. */
    private static final int IFF_PNG = 3;

    /** CCITT Group-4 fax-compressed TIFF (1 bpp). */
    private static final int IFF_TIFF_G4 = 8;

    /** Portable aNy Map (PBM/PGM/PPM); a 1 bpp image writes as binary P4. */
    private static final int IFF_PNM = 11;

    /**
     * The Leptonica {@code IFF_*} code to write {@code format} as.
     *
     * @param format the requested output format
     * @param sourceFormat the {@code IFF_*} the page was read from (returned as-is for {@link
     *     OutputFormat#SAME})
     * @return the Leptonica format code to pass to {@code pixWrite}
     */
    public static int toIff(OutputFormat format, int sourceFormat) {
        return switch (format) {
            case SAME -> sourceFormat;
            case PBM -> IFF_PNM;
            case PNG -> IFF_PNG;
            case TIFF -> IFF_TIFF_G4;
        };
    }
}
