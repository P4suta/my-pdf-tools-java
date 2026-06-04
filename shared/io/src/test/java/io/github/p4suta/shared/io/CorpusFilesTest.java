package io.github.p4suta.shared.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusFilesTest {

    @Test
    void collectFiltersByGlobAndSortsByFullPathString(@TempDir Path tmp) throws IOException {
        // A nested corpus with mixed extensions. The glob matches names only; the sort is by the
        // FULL walked path's string, so a deep path under a directory that sorts early comes before
        // a shallow sibling — exactly what register/despeckle E2E pins.
        Path sub = Files.createDirectories(tmp.resolve("aaa").resolve("deep"));
        Path b = Files.createFile(tmp.resolve("b.png"));
        Path deep = Files.createFile(sub.resolve("z.png"));
        Files.createFile(tmp.resolve("c.txt")); // wrong extension — excluded
        Files.createFile(tmp.resolve("a.PNG")); // case-sensitive glob — excluded

        List<Path> matched = CorpusFiles.collect(tmp, "*.png");

        // Sorted by Path::toString of the full path: ".../aaa/deep/z.png" < ".../b.png".
        assertThat(matched).containsExactly(deep, b);
    }

    @Test
    void collectReturnsEmptyWhenNothingMatches(@TempDir Path tmp) throws IOException {
        Files.createFile(tmp.resolve("only.txt"));

        assertThat(CorpusFiles.collect(tmp, "*.tif")).isEmpty();
    }

    @Test
    void collectMatchesFileNameNotPathSegments(@TempDir Path tmp) throws IOException {
        // The matcher sees the file NAME, so a glob with a slash never matches a flat name; an
        // extension glob matches regardless of how deep the directory tree is.
        Path deep = Files.createDirectories(tmp.resolve("x").resolve("y"));
        Path tif = Files.createFile(deep.resolve("page.tif"));

        assertThat(CorpusFiles.collect(tmp, "*.tif")).containsExactly(tif);
    }

    @Test
    void mirrorDestinationSwapsExtensionAndMirrorsNestedLayout(@TempDir Path tmp) {
        Path inputDir = tmp.resolve("in");
        Path outputDir = tmp.resolve("out");
        Path src = inputDir.resolve("chapter1").resolve("page-001.tiff");

        Path dest = CorpusFiles.mirrorDestination(src, inputDir, outputDir, "png");

        // Nested layout is mirrored under the output root; the extension is swapped.
        assertThat(dest).isEqualTo(outputDir.resolve("chapter1").resolve("page-001.png"));
    }

    @Test
    void mirrorDestinationKeepsExtensionWhenNull(@TempDir Path tmp) {
        Path inputDir = tmp.resolve("in");
        Path outputDir = tmp.resolve("out");
        Path src = inputDir.resolve("sub").resolve("page.jpg");

        // null extension is the `--format same` case: the source extension is preserved verbatim.
        Path dest = CorpusFiles.mirrorDestination(src, inputDir, outputDir, null);

        assertThat(dest).isEqualTo(outputDir.resolve("sub").resolve("page.jpg"));
    }

    @Test
    void mirrorDestinationAddsExtensionToExtensionlessName(@TempDir Path tmp) {
        Path inputDir = tmp.resolve("in");
        Path outputDir = tmp.resolve("out");
        Path src = inputDir.resolve("scan0001");

        // A name with no dot keeps its whole stem and gains the requested extension.
        Path dest = CorpusFiles.mirrorDestination(src, inputDir, outputDir, "tif");

        assertThat(dest).isEqualTo(outputDir.resolve("scan0001.tif"));
    }

    @Test
    void stripExtensionDropsLastDotSuffixOnly() {
        // Strips only the final extension; a dotless name is returned unchanged; a leading-dot name
        // keeps its empty stem behavior (lastIndexOf at 0).
        assertThat(CorpusFiles.stripExtension("page.001.tiff")).isEqualTo("page.001");
        assertThat(CorpusFiles.stripExtension("nodot")).isEqualTo("nodot");
        assertThat(CorpusFiles.stripExtension(".hidden")).isEmpty();
    }

    @Test
    void utilityClassHasNoInstances() throws ReflectiveOperationException {
        Constructor<CorpusFiles> ctor = CorpusFiles.class.getDeclaredConstructor();
        assertThat(ctor.canAccess(null)).isFalse();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
