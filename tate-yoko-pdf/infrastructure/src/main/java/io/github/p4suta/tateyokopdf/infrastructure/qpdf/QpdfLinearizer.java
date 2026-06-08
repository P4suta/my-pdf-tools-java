package io.github.p4suta.tateyokopdf.infrastructure.qpdf;

import io.github.p4suta.shared.pdf.QpdfRunner;
import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.ToolPath;
import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.PdfOutputPolicy;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the {@code qpdf} binary as an out-of-process modernization step. The invocation — command
 * array, the shared {@link ProcessRunner} run, and qpdf's exit-3 ("succeeded with warnings")
 * tolerance — is delegated to {@link QpdfRunner#modernizing}. This class adds two app-side
 * concerns: the THROW-on-failure policy ({@link ErrorKind#PDF_WRITE_FAILED}) and the bundled-binary
 * resolution chain below, which probes the in-jar {@code bin/} layout that {@code QpdfRunner}'s
 * {@code PATH}/{@code -D} resolution cannot reach. The resolved binary is handed to {@code
 * QpdfRunner} through the {@code QPDF_PATH_PROPERTY} {@code -D} override.
 *
 * <p>The modernizations:
 *
 * <ul>
 *   <li>{@code --linearize} — reorders bytes so the PDF can be streamed (Fast Web View / HTTP Range
 *       requests). qpdf packs the output into object streams as part of this step, so {@code
 *       --object-streams=generate} is not needed on top (verified empirically: adding it produces a
 *       byte-identical file).
 *   <li>{@code --min-version=X.Y} — rewrites the {@code %PDF-x.x} header byte to match {@link
 *       PdfOutputPolicy#TARGET}. PDFBox's {@code setVersion} updates only the catalog {@code
 *       /Version} entry for any value &ge; 1.4, so the header bump must happen here.
 *   <li>{@code --newline-before-endstream} — guarantees every stream object carries an EOL marker
 *       before the {@code endstream} keyword, which PDF/A (ISO 19005 clause 6.1.7.1) mandates.
 *       Required to keep {@code --pdf-a} files valid <em>after</em> linearisation, which otherwise
 *       repacks streams without that marker.
 * </ul>
 *
 * <p>The binary is resolved in this order:
 *
 * <ol>
 *   <li>The cross-app canonical {@code -Dp4suta.qpdf.path} override (see {@link
 *       ToolPath#canonicalKey}). The self-contained distribution convention sets this to the
 *       bundled binary, letting a uniform {@code natives/} layout — the one shared with the other
 *       tools — be found even though it is not a {@code bin/} sibling of the shadow JAR. Additive:
 *       unset in a dev-tree run, so resolution falls straight through to the bundle probe below.
 *   <li>The in-bundle copy: jpackage drops the input next to the shadow JAR under {@code app/}, and
 *       the upstream zip's layout puts the executable at {@code bin/qpdf} (or {@code
 *       bin\qpdf.exe}). {@link #resolveBundledQpdf()} probes the {@code bin/} subdirectory first,
 *       then a flat sibling layout for dev-tree runs.
 *   <li>{@code qpdf} on {@code PATH} — for dev runs on a machine that has qpdf installed.
 *   <li>Falls back to {@link PdfPostProcessor#noOp()} with a single warning. In that fallback the
 *       output PDF has its catalog {@code /Version} set to the target (most conformant readers
 *       honor it) but is not linearized; the header byte stays at PDFBox's internal default.
 * </ol>
 */
public final class QpdfLinearizer implements PdfPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(QpdfLinearizer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern PATH_SEPARATOR =
            Pattern.compile(Pattern.quote(File.pathSeparator));

    // -D override key passed to QpdfRunner, which takes a property KEY (not a Path); process() sets
    // it to the binary this class resolved. tate-namespaced to avoid colliding with another app's
    // qpdf override.
    private static final String QPDF_PATH_PROPERTY = "tateyokopdf.qpdf.path";

    // System properties are process-global, so the set-property -> QpdfRunner.linearize ->
    // restore-property window must be one critical section: otherwise concurrent process() calls
    // (e.g. parallel JUnit methods pinning different binaries to the shared key) could stomp each
    // other's override. The lock is uncontended in practice (production resolves the same binary).
    private static final Object QPDF_PROPERTY_LOCK = new Object();

    private final Path qpdfBinary;
    private final PdfVersion targetVersion;

    // Package-private so tests can pin the binary to a controlled failure mode (e.g. /bin/false).
    QpdfLinearizer(Path qpdfBinary, PdfVersion targetVersion) {
        this.qpdfBinary = qpdfBinary;
        this.targetVersion = targetVersion;
    }

    // For tests that don't care about the target version.
    QpdfLinearizer(Path qpdfBinary) {
        this(qpdfBinary, PdfOutputPolicy.TARGET);
    }

    /** Build the most capable {@link PdfPostProcessor} we can for the current environment. */
    public static PdfPostProcessor create() {
        Optional<Path> override = resolveCanonicalOverride();
        if (override.isPresent()) {
            log.info(
                    "qpdf binary resolved from -D{}: {}",
                    ToolPath.canonicalKey("qpdf"),
                    override.get());
            return new QpdfLinearizer(override.get(), PdfOutputPolicy.TARGET);
        }
        Optional<Path> bundled = resolveBundledQpdf();
        if (bundled.isPresent()) {
            log.info("qpdf binary resolved from bundle: {}", bundled.get());
            return new QpdfLinearizer(bundled.get(), PdfOutputPolicy.TARGET);
        }
        Optional<Path> onPath = resolveOnPath();
        if (onPath.isPresent()) {
            log.info("qpdf binary resolved from PATH: {}", onPath.get());
            return new QpdfLinearizer(onPath.get(), PdfOutputPolicy.TARGET);
        }
        log.warn(
                "qpdf binary not found. The following PDF modernizations are SKIPPED: "
                        + "(a) Fast Web View (linearisation), "
                        + "(b) header byte rewrite to %PDF-{}. "
                        + "Catalog /Version is still {} — most conformant readers honor it, "
                        + "but bundle a qpdf binary or add one to PATH for full conformance.",
                PdfOutputPolicy.TARGET.label(), PdfOutputPolicy.TARGET.label());
        return PdfPostProcessor.noOp();
    }

    @Override
    public void process(Path path) {
        if (!Files.isRegularFile(path)) {
            throw SpreadException.withDetail(
                    ErrorKind.PDF_WRITE_FAILED, "qpdf input missing: " + path, null);
        }
        // Write to a sibling temp file and move it over the target instead of --replace-input,
        // which leaves a "<name>.~qpdf-orig" backup whenever qpdf exits with warnings (code 3,
        // which we accept). The explicit out-file plus move yields an identical result without
        // littering the output directory.
        Path fileName = Objects.requireNonNull(path.getFileName());
        Path tmpOut = path.resolveSibling(fileName + ".qpdf-tmp");
        // QpdfRunner takes a property KEY, not a Path; ToolPath.resolve returns Path.of(override)
        // verbatim (no executable probe), so it runs exactly the binary this class resolved. The
        // set -> run -> restore is synchronized because the override property is process-global
        // (see QPDF_PROPERTY_LOCK); restored in the finally.
        synchronized (QPDF_PROPERTY_LOCK) {
            String previousOverride = System.getProperty(QPDF_PATH_PROPERTY);
            System.setProperty(QPDF_PATH_PROPERTY, qpdfBinary.toString());
            try {
                // QpdfRunner.modernizing builds the command array (--linearize,
                // --newline-before-endstream, --min-version=<target>, in, out) and tolerates qpdf's
                // exit 3 ("succeeded with warnings"): PDFBox's output routinely trips minor
                // warnings
                // yet is valid. .withTimeout pins tate's 60s over the runner's two-minute default.
                // Any OTHER non-zero exit (or launch failure) throws IOException, caught below as
                // PDF_WRITE_FAILED.
                QpdfRunner runner =
                        QpdfRunner.modernizing(QPDF_PATH_PROPERTY, targetVersion.label(), true)
                                .withTimeout(TIMEOUT);
                ProcessRunner.Result result = runner.linearize(path, tmpOut);
                Files.move(tmpOut, path, StandardCopyOption.REPLACE_EXISTING);
                log.debug("Linearized {} via qpdf exit={}", path.getFileName(), result.exitCode());
            } catch (TimeoutException e) {
                deleteQuietly(tmpOut);
                throw SpreadException.withDetail(
                        ErrorKind.PDF_WRITE_FAILED,
                        "qpdf timed out after " + TIMEOUT.toSeconds() + "s",
                        null);
            } catch (IOException e) {
                deleteQuietly(tmpOut);
                throw SpreadException.withDetail(
                        ErrorKind.PDF_WRITE_FAILED, "qpdf invocation failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deleteQuietly(tmpOut);
                throw SpreadException.withDetail(ErrorKind.INTERNAL, "qpdf interrupted", e);
            } finally {
                if (previousOverride == null) {
                    System.clearProperty(QPDF_PATH_PROPERTY);
                } else {
                    System.setProperty(QPDF_PATH_PROPERTY, previousOverride);
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Failed to delete qpdf temp file {}: {}", path, e.getMessage());
        }
    }

    // The cross-app canonical override the self-contained distribution convention sets
    // (-Dp4suta.qpdf.path=$APPDIR/natives/qpdf/bin/qpdf). qpdf is the one tool whose discovery is
    // CodeSource/PATH-based rather than -D-based (QpdfRunner's ToolPath resolution cannot reach an
    // in-bundle bin/ layout), and whose absence is SILENT (linearisation is skipped, the output is
    // still a valid PDF). This seam lets the bundle's qpdf — co-located with the other tools under
    // the uniform natives/ layout, not as a sibling of the shadow JAR — be found through the same
    // -Dp4suta.<tool>.path scheme the other tools use. Honored only when it points at a real
    // executable, so a stale/misconfigured value falls through to the bundle/PATH probes.
    static Optional<Path> resolveCanonicalOverride() {
        String override = System.getProperty(ToolPath.canonicalKey("qpdf"));
        if (override == null || override.isBlank()) {
            return Optional.empty();
        }
        Path candidate = Path.of(override);
        return Files.isExecutable(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    static Optional<Path> resolveBundledQpdf() {
        try {
            var codeSource = QpdfLinearizer.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return Optional.empty();
            }
            Path jarPath = Path.of(codeSource.getLocation().toURI());
            Path jarDir = jarPath.getParent();
            if (jarDir == null) {
                return Optional.empty();
            }
            return resolveBundledQpdfIn(jarDir);
        } catch (URISyntaxException | RuntimeException e) {
            log.debug("Could not derive bundled qpdf path from class location: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // Package-private so tests can drive the lookup with a synthetic directory, bypassing the
    // class-loader/CodeSource probe.
    static Optional<Path> resolveBundledQpdfIn(Path jarDir) {
        String executableName = osIsWindows() ? "qpdf.exe" : "qpdf";
        Path[] candidates = {
            jarDir.resolve("bin").resolve(executableName), // upstream zip layout
            jarDir.resolve(executableName), // legacy / flat dev-tree layout
        };
        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // Suppresses Error Prone's StringSplitter check (which wants Guava's Splitter): not worth a
    // Guava dependency for one PATH walk, and the trailing-empty semantics it warns about don't
    // matter here since empties are skipped below.
    @SuppressWarnings("StringSplitter")
    static Optional<Path> resolveOnPath() {
        String executableName = osIsWindows() ? "qpdf.exe" : "qpdf";
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }
        for (String entry : PATH_SEPARATOR.split(pathEnv)) {
            if (entry.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(entry, executableName);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean osIsWindows() {
        @Nullable String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }
}
