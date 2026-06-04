package io.github.p4suta.tateyokopdf.infrastructure.qpdf;

import io.github.p4suta.shared.pdf.QpdfRunner;
import io.github.p4suta.shared.process.ProcessRunner;
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
 * Drives the {@code qpdf} binary as an out-of-process step that performs two modernizations in one
 * pass. The invocation itself — the command array, the shared {@link ProcessRunner} run, and qpdf's
 * exit-3 ("succeeded with warnings") tolerance — is delegated to the cross-app {@link QpdfRunner}
 * capability ({@link QpdfRunner#modernizing}); this class keeps tate-yoko-pdf's two app-side
 * concerns layered over it: (a) the THROW-on-failure policy ({@link ErrorKind#PDF_WRITE_FAILED}),
 * and (b) the bundled-binary resolution chain below, which probes the in-jar {@code bin/} layout
 * that {@code QpdfRunner}'s {@code PATH}/{@code -D} resolution cannot reach. The resolved binary is
 * handed to {@code QpdfRunner} through the {@code QPDF_PATH_PROPERTY} {@code -D} override so the
 * shared runner resolves back to exactly the path this class picked.
 *
 * <p>The two modernizations:
 *
 * <ul>
 *   <li>{@code --linearize} — reorders bytes so the PDF can be streamed (Fast Web View / HTTP Range
 *       requests). qpdf packs the linearized output into object streams as part of this step, so we
 *       do not need {@code --object-streams=generate} on top (verified empirically — adding it
 *       produces a byte-identical file).
 *   <li>{@code --min-version=X.Y} — rewrites the {@code %PDF-x.x} header byte to match {@link
 *       PdfOutputPolicy#TARGET}. PDFBox's {@code setVersion} updates only the catalog {@code
 *       /Version} entry for any value &ge; 1.4, so the header bump must happen here.
 *   <li>{@code --newline-before-endstream} — guarantees every stream object carries an EOL marker
 *       before the {@code endstream} keyword, which PDF/A (ISO 19005 clause 6.1.7.1) mandates.
 *       Harmless for ordinary output (a single extra byte per stream); required to keep {@code
 *       --pdf-a} files valid <em>after</em> linearisation, since linearisation otherwise repacks
 *       streams without that marker.
 * </ul>
 *
 * <p>The binary is resolved in this order:
 *
 * <ol>
 *   <li>The in-bundle copy staged by {@code stageJpackageInput} from the upstream release zip.
 *       jpackage drops the input next to the shadow JAR under {@code app/}; the zip's native layout
 *       puts the executable at {@code bin/qpdf} (or {@code bin\qpdf.exe}). {@link
 *       #resolveBundledQpdf()} therefore probes the {@code bin/} subdirectory first, then falls
 *       back to a flat sibling layout for legacy dev-tree runs.
 *   <li>{@code which qpdf} / {@code where qpdf} on {@code PATH} — for dev runs from the source tree
 *       on a machine that has qpdf installed.
 *   <li>Falls back to {@link PdfPostProcessor#noOp()} and logs a single warning so a missing binary
 *       in a bundling failure surfaces audibly without breaking the whole pipeline. In that
 *       fallback the output PDF still has its catalog {@code /Version} set to the target (most
 *       conformant readers honor it) and is not linearized; the header byte stays at PDFBox's
 *       internal default.
 * </ol>
 */
public final class QpdfLinearizer implements PdfPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(QpdfLinearizer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern PATH_SEPARATOR =
            Pattern.compile(Pattern.quote(File.pathSeparator));

    // The -D override key through which we hand our app-side-resolved binary to the shared
    // QpdfRunner. QpdfRunner takes a property KEY (not a Path), so process() sets this property to
    // the binary this class resolved (bundle-first, then PATH) and QpdfRunner resolves back to
    // exactly it. The key is tate-namespaced so it never collides with another app's qpdf override.
    private static final String QPDF_PATH_PROPERTY = "tateyokopdf.qpdf.path";

    // System properties are process-global, so the set-property -> QpdfRunner.linearize ->
    // restore-property window must be one atomic critical section: without it two concurrent
    // process() calls (e.g. parallel JUnit methods pinning different test binaries to the shared
    // key) could stomp each other's override. Serializing qpdf calls is acceptable for a
    // post-processor and production always resolves the same binary, so the lock is uncontended in
    // practice.
    private static final Object QPDF_PROPERTY_LOCK = new Object();

    private final Path qpdfBinary;
    private final PdfVersion targetVersion;

    // Package-private so tests can pin the binary to a controlled failure mode
    // (e.g. /bin/false) without going through the production resolution chain.
    QpdfLinearizer(Path qpdfBinary, PdfVersion targetVersion) {
        this.qpdfBinary = qpdfBinary;
        this.targetVersion = targetVersion;
    }

    // Convenience overload for tests that don't care about the target version.
    QpdfLinearizer(Path qpdfBinary) {
        this(qpdfBinary, PdfOutputPolicy.TARGET);
    }

    /** Build the most capable {@link PdfPostProcessor} we can for the current environment. */
    public static PdfPostProcessor create() {
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
        // Write to a sibling temp file and move it over the target instead of using
        // --replace-input. qpdf's --replace-input leaves a "<name>.~qpdf-orig" backup next to
        // the output whenever it exits with warnings (code 3, which we accept), which litters
        // the user's output directory — very visible in batch runs. An explicit out-file plus a
        // move produces an identical linearized result while keeping the directory clean.
        Path fileName = Objects.requireNonNull(path.getFileName());
        Path tmpOut = path.resolveSibling(fileName + ".qpdf-tmp");
        // Hand the binary this class already resolved (bundle-first, then PATH; or a test-pinned
        // path via the package-private constructor) to the shared QpdfRunner, which takes a
        // property
        // KEY rather than a Path. ToolPath.resolve returns Path.of(override) verbatim (no
        // executable probe), so QpdfRunner runs exactly this binary. The set -> run -> restore is
        // one synchronized critical section because the override property is process-global (see
        // QPDF_PROPERTY_LOCK); restored in the finally so the property is left as we found it.
        synchronized (QPDF_PROPERTY_LOCK) {
            String previousOverride = System.getProperty(QPDF_PATH_PROPERTY);
            System.setProperty(QPDF_PATH_PROPERTY, qpdfBinary.toString());
            try {
                // QpdfRunner.modernizing builds the identical command array (--linearize,
                // --newline-before-endstream, --min-version=<target>, in, out) and bakes in qpdf's
                // exit-3 ("succeeded with warnings") tolerance — PDFBox's container routinely trips
                // minor qpdf warnings yet the linearized output is valid. The default timeout is
                // two
                // minutes, so .withTimeout pins tate's 60s. The shared runner throws an IOException
                // for any OTHER non-zero exit (or a launch failure); that surfaces through the
                // catch (IOException) below as our PDF_WRITE_FAILED policy — the throw-on-failure
                // decision stays app-side, layered over the runner's Result.
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

    // Package-private so tests can drive the lookup with a synthetic directory
    // without going through the class-loader/CodeSource probe.
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

    // Error Prone's StringSplitter check wants Guava's Splitter; pulling Guava in for one PATH
    // walk is not worth it. `Pattern.compile(quote(File.pathSeparator)).split(...)` would still
    // trip the check, and the surprising trailing-empty semantics that StringSplitter warns about
    // don't matter here (we explicitly skip empties below).
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
