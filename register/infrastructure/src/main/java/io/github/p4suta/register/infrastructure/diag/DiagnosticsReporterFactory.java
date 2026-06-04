package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.port.Reporter;
import io.github.p4suta.register.port.ReporterFactory;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The {@link ReporterFactory} adapter: builds a {@link Diagnostics} collector rooted at the
 * requested diagnostics directory. The {@code :app} composition root supplies this to the
 * application services so they can open a per-run reporter without seeing the concrete {@code
 * :infrastructure} type.
 */
public final class DiagnosticsReporterFactory implements ReporterFactory {

    public DiagnosticsReporterFactory() {}

    @Override
    public Reporter create(Path diagDir, boolean flipbook) throws IOException {
        return new Diagnostics(diagDir, flipbook);
    }

    @Override
    public Reporter noOp() {
        return Reporter.noOp();
    }
}
