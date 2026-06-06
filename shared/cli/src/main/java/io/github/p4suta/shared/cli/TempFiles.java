package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates temp files that are removed even when the JVM is interrupted (Ctrl-C / SIGTERM).
 *
 * <p>A plain {@code try/finally} around {@link Files#createTempFile} leaks on a signal: the {@code
 * finally} block never runs when the JVM is killed mid-action. Files created here are tracked in a
 * registry that a single, lazily-installed shutdown hook drains on exit, so an interrupted run
 * leaves nothing behind. On a normal completion the caller's {@code finally} calls {@link
 * #delete(Path)}, which removes the file and deregisters it so the hook has nothing to do.
 */
public final class TempFiles {

    private static final Set<Path> REGISTERED = ConcurrentHashMap.newKeySet();

    private static final Object HOOK_LOCK = new Object();
    private static boolean hookInstalled;

    private TempFiles() {}

    /**
     * Creates a temp file named {@code <prefix>…<suffix>} and registers it for shutdown-time
     * deletion, installing the cleanup hook on first use.
     *
     * @param prefix the temp-file name prefix
     * @param suffix the temp-file name suffix including any dot (e.g. {@code ".pdf"})
     * @return the created temp file
     * @throws IOException if the file cannot be created
     */
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        ensureHook();
        Path file = Files.createTempFile(prefix, suffix);
        REGISTERED.add(file);
        return file;
    }

    /**
     * Deletes {@code file} if it exists and deregisters it, so the shutdown hook will not revisit
     * it. Safe to call on a path not created here (it is simply deleted and absent from the
     * registry).
     *
     * @param file the temp file to remove
     * @throws IOException if the file exists but cannot be deleted
     */
    public static void delete(Path file) throws IOException {
        REGISTERED.remove(file);
        Files.deleteIfExists(file);
    }

    private static void ensureHook() {
        synchronized (HOOK_LOCK) {
            if (hookInstalled) {
                return;
            }
            Runtime.getRuntime()
                    .addShutdownHook(new Thread(TempFiles::deleteAll, "tempfiles-cleanup"));
            hookInstalled = true;
        }
    }

    private static void deleteAll() {
        for (Path file : REGISTERED) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Best-effort on shutdown: nothing useful to do if a temp file resists deletion.
            }
        }
    }
}
