package io.github.p4suta.tateyokopdf.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Smoke test for the built jpackage launcher: convert a sample PDF and assert the output really is
 * a PDF (starts with the {@code %PDF} magic). One cross-platform check replacing the former per-OS
 * shell / PowerShell CI steps — the Gradle task supplies the OS-specific launcher path. Exits
 * non-zero with a message on any failure.
 *
 * <p>Usage: {@code SmokeCheck <launcher> <input.pdf> <output.pdf>}
 */
public final class SmokeCheck {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private SmokeCheck() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            fail("usage: SmokeCheck <launcher> <input.pdf> <output.pdf>");
            return;
        }
        Path launcher = Path.of(args[0]);
        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);

        if (!Files.isExecutable(launcher)) {
            fail("launcher not found or not executable: " + launcher);
        }
        Files.deleteIfExists(output);

        Process process =
                new ProcessBuilder(launcher.toString(), input.toString(), "-o", output.toString())
                        .inheritIO()
                        .start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            fail("launcher timed out");
        }
        if (process.exitValue() != 0) {
            fail("launcher exited " + process.exitValue());
        }
        if (!startsWithPdfMagic(output)) {
            fail("output is not a PDF (missing %PDF magic): " + output);
        }
        System.out.println("✓ jpackage CLI smoke passed (" + output + ")");
    }

    private static boolean startsWithPdfMagic(Path pdf) throws IOException {
        if (!Files.isRegularFile(pdf)) {
            return false;
        }
        byte[] head = new byte[PDF_MAGIC.length];
        try (var in = Files.newInputStream(pdf)) {
            return in.readNBytes(head, 0, head.length) == head.length
                    && Arrays.equals(head, PDF_MAGIC);
        }
    }

    private static void fail(String message) {
        System.err.println("smoke check failed: " + message);
        System.exit(1);
    }
}
