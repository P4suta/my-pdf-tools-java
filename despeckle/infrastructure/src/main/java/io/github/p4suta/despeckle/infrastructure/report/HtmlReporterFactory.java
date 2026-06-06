package io.github.p4suta.despeckle.infrastructure.report;

import io.github.p4suta.despeckle.port.Reporter;
import io.github.p4suta.despeckle.port.ReporterFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds {@link HtmlReporter} instances — the {@link ReporterFactory} adapter the {@code :app}
 * composition root supplies to the application. Creating a reporter lays out the {@code
 * before}/{@code overlay}/{@code after} panel directories up front, in {@link #create(Path,
 * boolean)}.
 */
public final class HtmlReporterFactory implements ReporterFactory {

    public HtmlReporterFactory() {}

    /**
     * Create the report directory tree and a ready {@link HtmlReporter}.
     *
     * @param flipbook whether to assemble the animated-WebP overlay flip-book at finish
     */
    @Override
    public Reporter create(Path reportDir, boolean flipbook) throws IOException {
        for (String panel : List.of("before", "overlay", "after")) {
            Files.createDirectories(reportDir.resolve(panel));
        }
        return new HtmlReporter(reportDir, flipbook);
    }

    /** A pass-through reporter for when reporting is disabled. */
    @Override
    public Reporter noOp() {
        return Reporter.noOp();
    }
}
