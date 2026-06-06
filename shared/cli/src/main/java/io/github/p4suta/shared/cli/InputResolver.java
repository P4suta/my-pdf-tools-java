package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.cli.ParseException;

/**
 * Expands the raw positional arguments into the concrete list of source files to process.
 *
 * <ul>
 *   <li>{@value #STDIN_MARKER} means "read a single input from stdin" and must be the only input.
 *   <li>a directory expands to its direct children that match a caller-supplied {@link
 *       Predicate}&lt;{@link Path}&gt; (e.g. an extension or glob filter), sorted by file name,
 *       non-recursively.
 *   <li>any other path is taken verbatim — existence is validated later by the processing pipeline
 *       so a missing file surfaces as the pipeline's own not-found error rather than a generic
 *       resolver error.
 * </ul>
 *
 * <p>The directory filter is a parameter rather than a hardcoded test, so each app supplies its own
 * — see {@link #globFilter(String)} for the common case of a single glob pattern.
 */
public final class InputResolver {

    /** The positional token that requests a single input from {@code System.in}. */
    public static final String STDIN_MARKER = "-";

    /**
     * Outcome of resolving the positional arguments.
     *
     * @param stdin whether the single input is to be read from {@code System.in}
     * @param files the resolved files when {@link #stdin()} is {@code false} (empty otherwise)
     */
    public record Resolved(boolean stdin, List<Path> files) {

        /**
         * Canonical constructor; defensively copies {@code files} so the record stays immutable.
         */
        public Resolved {
            files = List.copyOf(files);
        }
    }

    private InputResolver() {}

    /**
     * Resolves {@code rawInputs} into concrete files (or a stdin request), expanding any directory
     * argument with {@code dirFilter}.
     *
     * @param rawInputs the raw positional arguments
     * @param dirFilter the predicate a directory entry must satisfy to be included
     * @return the resolved inputs
     * @throws ParseException if {@value #STDIN_MARKER} is combined with other inputs, or a
     *     directory cannot be read
     */
    public static Resolved resolve(List<String> rawInputs, Predicate<Path> dirFilter)
            throws ParseException {
        Objects.requireNonNull(dirFilter, "dirFilter");
        if (rawInputs.stream().anyMatch(InputResolver::isStdin)) {
            if (rawInputs.size() != 1) {
                throw new ParseException(
                        "'" + STDIN_MARKER + "' (stdin) cannot be combined with other inputs");
            }
            return new Resolved(true, List.of());
        }

        List<Path> files = new ArrayList<>();
        for (String raw : rawInputs) {
            Path path = Path.of(raw);
            if (Files.isDirectory(path)) {
                files.addAll(listMatching(path, dirFilter));
            } else {
                files.add(path);
            }
        }
        return new Resolved(false, files);
    }

    /**
     * A directory filter accepting regular files whose name matches {@code glob} (e.g. {@code
     * *.pdf} or {@code *.{pbm,png,tiff,tif}}). Glob matching is applied to the file name only, so
     * it is independent of the directory the entry lives in.
     *
     * <p>Matching is case-insensitive: both the pattern and each file name are lower-cased before
     * comparison, so {@code *.pdf} also matches {@code SCAN.PDF} and {@code page.TIF} on
     * case-sensitive file systems (Linux) where the glob would otherwise drop them.
     *
     * @param glob the glob pattern to match against each entry's file name
     * @return a predicate suitable for {@link #resolve(List, Predicate)}
     */
    public static Predicate<Path> globFilter(String glob) {
        PathMatcher matcher =
                FileSystems.getDefault().getPathMatcher("glob:" + glob.toLowerCase(Locale.ROOT));
        return path -> {
            Path name = path.getFileName();
            return Files.isRegularFile(path)
                    && name != null
                    && matcher.matches(Path.of(name.toString().toLowerCase(Locale.ROOT)));
        };
    }

    private static List<Path> listMatching(Path dir, Predicate<Path> dirFilter)
            throws ParseException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(dirFilter)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new ParseException("cannot read directory '" + dir + "': " + e.getMessage());
        }
    }

    private static boolean isStdin(String raw) {
        return STDIN_MARKER.equals(raw);
    }
}
