package io.github.p4suta.shared.io;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Corpus discovery and output-path mirroring shared by every directory-walking pipeline: collect
 * the matching input pages in a deterministic order, and map each input to its mirrored output path
 * under a separate output root.
 *
 * <p>Two contracts are load-bearing and reproduced exactly from the apps:
 *
 * <ul>
 *   <li>the sort order is by the full walked path's string ({@code
 *       Comparator.comparing(Path::toString)}), so the pages are processed in the same order every
 *       run — register's and despeckle's end-to-end tests pin this;
 *   <li>the selection filter is a caller-supplied glob, never a hardcoded extension set, so each
 *       app keeps its own input pattern.
 * </ul>
 */
public final class CorpusFiles {

    private CorpusFiles() {}

    /**
     * Every regular file under {@code root} whose file NAME matches {@code glob}, sorted by full
     * path string.
     *
     * <p>The glob is matched against each entry's {@linkplain Path#getFileName() file name} only
     * (e.g. {@code *.tif}), via the default filesystem's {@code glob:}-syntax {@link PathMatcher};
     * the directory tree is walked recursively. The result is ordered by {@link Path#toString()} of
     * the full path so the processing order is stable across runs.
     *
     * @param root the directory to walk recursively
     * @param glob the file-name glob (without the {@code glob:} syntax prefix), e.g. {@code *.png}
     * @return the matching regular files, sorted by full path string
     * @throws IOException if walking {@code root} fails
     */
    public static List<Path> collect(Path root, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(
                            p -> {
                                Path name = p.getFileName();
                                return name != null && matcher.matches(name);
                            })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /**
     * The output path for {@code src}: its {@code inputDir}-relative path resolved under {@code
     * outputDir}, with the file extension swapped to {@code extension} — or kept unchanged when
     * {@code extension} is {@code null} (the {@code --format same} case) or {@code src} has no file
     * name.
     *
     * <p>The replacement extension is supplied as a plain string so this helper stays free of any
     * app's output-format enum: each app passes its own {@code OutputFormat.extension()}.
     *
     * @param src the source page path (must be under {@code inputDir})
     * @param inputDir the input root the mirroring is relative to
     * @param outputDir the output root to mirror into
     * @param extension the replacement extension WITHOUT a leading dot (e.g. {@code "png"}), or
     *     {@code null} to keep the source's extension
     * @return the mirrored output path
     */
    public static Path mirrorDestination(
            Path src, Path inputDir, Path outputDir, @Nullable String extension) {
        Path relative = inputDir.relativize(src);
        Path dest = outputDir.resolve(relative);
        Path name = dest.getFileName();
        if (extension == null || name == null) {
            return dest;
        }
        return dest.resolveSibling(stripExtension(name.toString()) + "." + extension);
    }

    /** {@code fileName} up to (but not including) its last {@code '.'}, or whole if it has none. */
    static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
