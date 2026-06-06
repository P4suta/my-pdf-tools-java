package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InputResolverTest {

    private static final Predicate<Path> PDF = InputResolver.globFilter("*.pdf");

    @Test
    void stdinMarkerAloneResolvesToStdin() throws Exception {
        InputResolver.Resolved resolved = InputResolver.resolve(List.of("-"), PDF);

        assertThat(resolved.stdin()).isTrue();
        assertThat(resolved.files()).isEmpty();
    }

    @Test
    void stdinMarkerCombinedWithOtherInputsIsRejected() {
        assertThatThrownBy(() -> InputResolver.resolve(List.of("-", "a.pdf"), PDF))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("stdin");
    }

    @Test
    void singleFileIsTakenVerbatimWithoutExistenceCheck() throws Exception {
        InputResolver.Resolved resolved = InputResolver.resolve(List.of("missing.pdf"), PDF);

        assertThat(resolved.stdin()).isFalse();
        assertThat(resolved.files()).containsExactly(Path.of("missing.pdf"));
    }

    @Test
    void directoryExpandsToFilteredSortedChildren(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("b.pdf"), "b");
        Files.writeString(dir.resolve("a.pdf"), "a");
        Files.writeString(dir.resolve("note.txt"), "skip");
        Files.createDirectory(dir.resolve("sub.pdf")); // a directory must not match the file glob

        InputResolver.Resolved resolved = InputResolver.resolve(List.of(dir.toString()), PDF);

        assertThat(resolved.stdin()).isFalse();
        assertThat(resolved.files()).containsExactly(dir.resolve("a.pdf"), dir.resolve("b.pdf"));
    }

    @Test
    void directoryFilterIsParameterizedNotHardcodedToPdf(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("scan.png"), "img");
        Files.writeString(dir.resolve("doc.pdf"), "pdf");

        InputResolver.Resolved resolved =
                InputResolver.resolve(
                        List.of(dir.toString()), InputResolver.globFilter("*.{png,tif}"));

        assertThat(resolved.files()).containsExactly(dir.resolve("scan.png"));
    }

    @Test
    void braceGlobMatchesAnyListedExtension(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.tif"), "t");
        Files.writeString(dir.resolve("b.pbm"), "p");
        Files.writeString(dir.resolve("c.jpg"), "skip");

        InputResolver.Resolved resolved =
                InputResolver.resolve(
                        List.of(dir.toString()), InputResolver.globFilter("*.{pbm,png,tiff,tif}"));

        assertThat(resolved.files()).containsExactly(dir.resolve("a.tif"), dir.resolve("b.pbm"));
    }

    @Test
    void globMatchesUppercaseExtensionsCaseInsensitively(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("SCAN.PDF"), "u");
        Files.writeString(dir.resolve("doc.Pdf"), "m");
        Files.writeString(dir.resolve("plain.pdf"), "l");
        Files.writeString(dir.resolve("note.txt"), "skip");

        InputResolver.Resolved resolved = InputResolver.resolve(List.of(dir.toString()), PDF);

        assertThat(resolved.files())
                .containsExactly(
                        dir.resolve("SCAN.PDF"), dir.resolve("doc.Pdf"), dir.resolve("plain.pdf"));
    }

    @Test
    void unreadableDirectoryArgRaisesParseException(@TempDir Path dir) throws Exception {
        Path unreadable = Files.createDirectory(dir.resolve("locked"));
        Set<PosixFilePermission> none = PosixFilePermissions.fromString("---------");
        try {
            Files.setPosixFilePermissions(unreadable, none);
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions unsupported on this file system");
        }
        // Files.list on a directory we cannot read raises IOException; running as root ignores the
        // permission bits, so skip rather than fail there.
        assumeTrue(!Files.isReadable(unreadable), "running as root: permission bits ignored");

        assertThatThrownBy(() -> InputResolver.resolve(List.of(unreadable.toString()), PDF))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("cannot read directory");
    }

    @Test
    void mixesFilesAndDirectories(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("z.pdf"), "z");

        InputResolver.Resolved resolved =
                InputResolver.resolve(List.of("loose.pdf", dir.toString()), PDF);

        assertThat(resolved.files()).containsExactly(Path.of("loose.pdf"), dir.resolve("z.pdf"));
    }

    @Test
    void resolvedRecordCopiesItsFilesDefensively() {
        java.util.List<Path> mutable = new java.util.ArrayList<>();
        mutable.add(Path.of("a"));

        InputResolver.Resolved resolved = new InputResolver.Resolved(false, mutable);
        mutable.add(Path.of("b"));

        assertThat(resolved.files()).containsExactly(Path.of("a"));
    }
}
