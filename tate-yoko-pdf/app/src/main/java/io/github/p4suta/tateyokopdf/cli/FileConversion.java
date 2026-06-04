package io.github.p4suta.tateyokopdf.cli;

import io.github.p4suta.shared.cli.OutputTarget;
import io.github.p4suta.tateyokopdf.application.SpreadOptions;
import io.github.p4suta.tateyokopdf.application.SpreadService;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.port.DocumentFactory;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Converts one source PDF into a spread layout using a fixed pipeline and per-run options.
 *
 * <p>Holds the collaborators assembled once at the {@link SpreadCommand} composition root plus the
 * parsed {@link CliArguments}, so the stdin/single/batch dispatch only supplies the input, where it
 * goes, and a progress label.
 */
final class FileConversion {

    private final DocumentFactory factory;
    private final SpreadLayoutCalculator calculator;
    private final PdfPostProcessor postProcessor;
    private final CliArguments args;

    FileConversion(
            DocumentFactory factory,
            SpreadLayoutCalculator calculator,
            PdfPostProcessor postProcessor,
            CliArguments args) {
        this.factory = factory;
        this.calculator = calculator;
        this.postProcessor = postProcessor;
        this.args = args;
    }

    /**
     * Converts {@code input}, writing to {@code target} (a file or stdout) and tagging progress
     * with {@code label} (null for non-batch runs).
     */
    void convert(Path input, OutputTarget target, @Nullable String label) throws IOException {
        target.write(
                realOut -> {
                    var options =
                            new SpreadOptions(
                                    input,
                                    realOut,
                                    args.direction(),
                                    args.firstPageMode(),
                                    args.pdfA());
                    new SpreadService(
                                    factory,
                                    calculator,
                                    postProcessor,
                                    new ConsoleProgressListener(label))
                            .execute(options);
                });
    }
}
