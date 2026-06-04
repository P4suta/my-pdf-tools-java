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
 * Pins the architectural boundaries of the cross-app shared modules ({@code
 * io.github.p4suta.shared..}) that the per-app architecture tests can no longer see.
 *
 * <p>Each app's {@code ArchitectureTest} scans {@code @AnalyzeClasses(io.github.p4suta.<app>)},
 * which does NOT include {@code io.github.p4suta.shared}. So the Foreign Function &amp; Memory and
 * {@code MethodHandle} confinement that USED to be enforced on register's/despeckle's {@code
 * leptonica} package became UNENFORCED once the binding moved into the shared imaging island — the
 * per-app rules now pass vacuously, because the shared package is outside their imported graph. This
 * suite re-establishes that confinement by importing the shared graph directly (production classes
 * only) and pinning the same FFM/{@code MethodHandle} island, the absence of package cycles, and the
 * single JSpecify nullness vocabulary.
 *
 * <p>The ArchUnit idioms mirror register's existing {@code app/.../ArchitectureTest.java}: the FFM
 * rule excludes the two binding classes by fully-qualified name and forbids everyone else to depend
 * on {@code java.lang.foreign..}; the {@code MethodHandle} rule is the same shape against {@code
 * java.lang.invoke..}; cycles use {@code slices().beFreeOfCycles()}; and the nullness rule reuses
 * register's "{@code @Nullable}/{@code @NonNull}/... not from JSpecify" predicate.
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
     * ProcessBuilder}/{@code Process}: the JBIG2 assembler keeps a single documented local {@code
     * ProcessBuilder} for the {@code jbig2 -p} encode, whose RAW binary stdout the shared {@code
     * ProcessRunner} would corrupt (it decodes stdout to a UTF-8 String).
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
     * The Foreign Function &amp; Memory API ({@code java.lang.foreign..}) is the one piece of native,
     * "restricted" surface in the shared graph; it lives behind exactly two classes — {@code Pix},
     * the RAII handle, and {@code Leptonica}, the binding island — so the rest of the shared code
     * stays plain, safe Java. This is the rule the per-app {@code @AnalyzeClasses} graphs can no
     * longer enforce on the (now shared) imaging island; it is enforced here.
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
     * {@code MethodHandles}) stay inside the binding island alone: only {@code Leptonica} holds the
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
     * Nullability across the shared modules is spoken in exactly one vocabulary — JSpecify — because
     * that is what NullAway reads. Mirrors register's existing rule.
     */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);

    /**
     * Apache Commons CLI ({@code org.apache.commons.cli..}) is the argument-parsing front end, and
     * the {@code io.github.p4suta.shared.cli} module — the cross-app CLI scaffolding generalized
     * from tate-yoko-pdf's front end — is the ONE shared island allowed to touch it. Everything
     * below the shell (the kernel primitives, the observability layer, the imaging binding) handles
     * failures through the shared error model, never the CLI parser's {@code ParseException}. This
     * is the rule the per-app {@code @AnalyzeClasses} graphs can no longer enforce on the (now
     * shared) CLI scaffolding; it is enforced here. Mirrors register's {@code commonsCliConfinedToCli},
     * using the package-exclusion idiom because the allowed set is a whole package, not named classes.
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
     * Throwable.printStackTrace()}) are confined to the {@code io.github.p4suta.shared.cli} module —
     * the one shared island deliberately allowed to write to them, because a CLI front end must
     * print help, usage errors and the throwable report itself (the front end is Apache Commons
     * CLI, and the batch driver / exception handler report to {@code System.err}). The kernel,
     * observability and imaging islands stay stream-free and route everything through SLF4J;
     * notably {@code FatalUncaughtHandler} does NOT touch the standard streams, so no exemption is
     * needed. Mirrors register's {@code noStandardStreams}, reusing ArchUnit's {@code
     * ACCESS_STANDARD_STREAMS} predicate (which also flags {@code printStackTrace}).
     */
    @ArchTest
    static final ArchRule standardStreamsConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("io.github.p4suta.shared.cli..")
                    .should(ACCESS_STANDARD_STREAMS);

    /**
     * Launching external processes is infrastructure: {@code java.lang.ProcessBuilder} (the launcher)
     * and {@code java.lang.Process} (the running child it hands back) live behind the {@code
     * io.github.p4suta.shared.process} module — the cross-app external-process island ({@code
     * ProcessRunner} shells out and drains stdout/stderr) generalized out of the per-app
     * infrastructure now that external-process running lives in shared. Keep {@code ProcessBuilder}
     * and {@code Process} out of the kernel, observability, imaging and CLI islands; everything below
     * the launcher works through {@code ProcessRunner}'s captured result. This is the rule the
     * per-app {@code @AnalyzeClasses} graphs can no longer enforce on the (now shared) exec helper; it
     * is enforced here. Mirrors register's {@code processBuilderConfinedToInfrastructure}, using the
     * package-exclusion idiom (the allowed set is a whole package) and the predicate overload of
     * {@code dependOnClassesThat} (as {@code nullnessAnnotationsComeFromJSpecify} does) because the
     * fluent {@code haveFullyQualifiedName} pins a single class, whereas both JDK types are confined
     * here.
     *
     * <p>{@code PdfBoxJbig2Assembler} (in {@code io.github.p4suta.shared.pdf}) is the one exemption —
     * the same shape as FFM exempting {@code Pix}/{@code Leptonica} by fully-qualified name: the
     * {@code jbig2 -p} encode writes a RAW binary JBIG2 stream to stdout, which {@code ProcessRunner}
     * (it decodes stdout to a UTF-8 String) would corrupt, so the assembler keeps a single documented
     * local {@code ProcessBuilder} that redirects stdout straight to a scratch file. The exemption is
     * pinned to that one class by name (not the whole {@code shared.pdf} package), so {@code
     * QpdfRunner} / {@code PdfImagesCliExtractor} — which DO route through {@code ProcessRunner} — and
     * any future pdf class still trip this rule if they grab a raw {@code ProcessBuilder}.
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
     * Apache PDFBox ({@code org.apache.pdfbox..}) — and the {@code org.apache.xmpbox..} packet
     * library that ships with it and shares its version — is the one PDF toolkit in the shared graph,
     * used only to assemble the lossless-JBIG2 output PDF (the {@code /JBIG2Decode} image XObjects,
     * the page tree, the Info-dict copy, the XMP packet) inside the {@code
     * io.github.p4suta.shared.pdf} module — the cross-app PDF I/O island generalized from despeckle's
     * adapters now that PDFBox lives there among the shared modules. Keep both PDFBox and xmpbox out
     * of the kernel, observability, imaging, CLI and process islands; everything below the assembler
     * works through {@code PdfBoxJbig2Assembler}'s file output, never a PDFBox type. This is the rule
     * the per-app {@code @AnalyzeClasses} graphs can no longer enforce on the (now shared) PDF
     * assembly; it is enforced here. Mirrors register's {@code pdfBoxConfinedToInfrastructure} and the
     * {@code commonsCliConfinedToCli} idiom above, using the package-exclusion form because the
     * allowed set is a whole package (and the forbidden set is two whole packages), not named classes.
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
