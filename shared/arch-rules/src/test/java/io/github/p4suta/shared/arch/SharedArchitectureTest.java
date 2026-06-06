package io.github.p4suta.shared.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Pins the architectural boundaries of the shared modules ({@code io.github.p4suta.shared..}), which
 * the per-app architecture tests do not import (each scans only {@code
 * @AnalyzeClasses(io.github.p4suta.<app>)}). Imports the shared graph directly (production classes
 * only) and pins the FFM/{@code MethodHandle} confinement, the absence of package cycles, and the
 * single JSpecify nullness vocabulary.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.shared",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SharedArchitectureTest {

    /** The two classes — and only these two — allowed to touch the Foreign Function &amp; Memory API. */
    private static final String PIX = "io.github.p4suta.shared.imaging.Pix";

    private static final String LEPTONICA = "io.github.p4suta.shared.imaging.Leptonica";

    /**
     * The one class outside {@code io.github.p4suta.shared.process} allowed to hold a raw {@code
     * ProcessBuilder}/{@code Process}: the JBIG2 assembler keeps a local {@code ProcessBuilder} for
     * the {@code jbig2 -p} encode, whose raw binary stdout {@code ProcessRunner} would corrupt (it
     * decodes stdout to a UTF-8 String).
     */
    private static final String PDF_JBIG2_ASSEMBLER =
            "io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler";

    /** Any nullness annotation ({@code @Nullable}/{@code @NonNull}/...) not from JSpecify. */
    private static final DescribedPredicate<JavaClass> NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY =
            simpleName("Nullable")
                    .or(simpleName("NonNull"))
                    .or(simpleName("Nonnull"))
                    .or(simpleName("NotNull"))
                    .and(not(resideInAPackage("org.jspecify.annotations")))
                    .as("a nullness annotation not from JSpecify");

    /**
     * The Foreign Function &amp; Memory API ({@code java.lang.foreign..}) lives behind exactly two
     * classes — {@code Pix}, the RAII handle, and {@code Leptonica}, the binding — so the rest of the
     * shared code stays plain, safe Java.
     */
    @ArchTest
    static final ArchRule foreignMemoryApiConfinedToPixAndLeptonica =
            noClasses()
                    .that()
                    .doNotHaveFullyQualifiedName(PIX)
                    .and()
                    .doNotHaveFullyQualifiedName(LEPTONICA)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("java.lang.foreign..");

    /**
     * Raw downcall handles ({@code java.lang.invoke..}, which contains both {@code MethodHandle} and
     * {@code MethodHandles}) stay inside the binding class alone: only {@code Leptonica} holds the
     * cached downcall handles. {@code Pix} works through {@code Leptonica}, so it needs FFM but not
     * the invoke machinery.
     */
    @ArchTest
    static final ArchRule methodHandlesConfinedToLeptonica =
            noClasses()
                    .that()
                    .doNotHaveFullyQualifiedName(LEPTONICA)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("java.lang.invoke..");

    /** No package within the shared graph may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.shared.(*)..").should().beFreeOfCycles();

    /**
     * Nullability annotations across the shared modules come only from JSpecify, which is what
     * NullAway reads.
     */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);

    /**
     * Apache Commons CLI ({@code org.apache.commons.cli..}) is confined to the {@code
     * io.github.p4suta.shared.cli} module. Everything below the shell (kernel, observability,
     * imaging) handles failures through the shared error model, never the CLI parser's {@code
     * ParseException}.
     */
    @ArchTest
    static final ArchRule commonsCliConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("io.github.p4suta.shared.cli..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.commons.cli..");

    /**
     * Standard streams ({@code System.out}/{@code System.err}, including {@code
     * Throwable.printStackTrace()}) are confined to the {@code io.github.p4suta.shared.cli} module,
     * which must print help, usage errors, and the throwable report. The kernel, observability and
     * imaging modules stay stream-free and route through SLF4J; {@code FatalUncaughtHandler} does
     * not touch the standard streams, so it needs no exemption. The {@code ACCESS_STANDARD_STREAMS}
     * predicate also flags {@code printStackTrace}.
     */
    @ArchTest
    static final ArchRule standardStreamsConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("io.github.p4suta.shared.cli..")
                    .should(ACCESS_STANDARD_STREAMS);

    /**
     * {@code java.lang.ProcessBuilder} and {@code java.lang.Process} are confined to the {@code
     * io.github.p4suta.shared.process} module; everything else works through {@code ProcessRunner}'s
     * captured result.
     *
     * <p>{@code PdfBoxJbig2Assembler} (in {@code io.github.p4suta.shared.pdf}) is the one exemption:
     * the {@code jbig2 -p} encode writes a raw binary JBIG2 stream to stdout, which {@code
     * ProcessRunner} (decoding stdout to a UTF-8 String) would corrupt, so the assembler keeps a
     * local {@code ProcessBuilder} that redirects stdout to a scratch file. The exemption is pinned
     * to that one class by name, so {@code QpdfRunner} / {@code PdfImagesCliExtractor} and any future
     * pdf class still trip this rule if they grab a raw {@code ProcessBuilder}.
     */
    @ArchTest
    static final ArchRule processApiConfinedToProcessModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("io.github.p4suta.shared.process..")
                    .and()
                    .doNotHaveFullyQualifiedName(PDF_JBIG2_ASSEMBLER)
                    .should()
                    .dependOnClassesThat(belongToAnyOf(ProcessBuilder.class, Process.class));

    /**
     * Apache PDFBox ({@code org.apache.pdfbox..}) and the {@code org.apache.xmpbox..} packet library
     * that ships with it are confined to the {@code io.github.p4suta.shared.pdf} module, used only to
     * assemble the lossless-JBIG2 output PDF (the {@code /JBIG2Decode} image XObjects, the page tree,
     * the Info-dict copy, the XMP packet). Everything else works through {@code
     * PdfBoxJbig2Assembler}'s file output, never a PDFBox type.
     */
    @ArchTest
    static final ArchRule pdfBoxConfinedToPdfModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("io.github.p4suta.shared.pdf..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.pdfbox..", "org.apache.xmpbox..");
}
