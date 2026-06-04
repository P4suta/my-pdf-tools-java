package io.github.p4suta.tateyokopdf.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Boundary rules that the Gradle module graph cannot already enforce.
 *
 * <p>Since the split into {@code :domain}, {@code :port}, {@code :application}, {@code
 * :infrastructure}, and {@code :app}, most of the original onion rules are guaranteed at compile
 * time by the absence of a {@code project()} dependency. What remains, and what this suite pins,
 * are the intra-graph, class-level conventions a missing dependency does not catch: the hexagonal
 * layering re-asserted within the single imported graph, the pure {@code domain}/{@code port}
 * staying free of filesystem I/O, PDFBox and {@code ProcessBuilder} pinned to their adapters,
 * Commons CLI pinned to the CLI shell, standard-stream access kept to the CLI/diagnostic front
 * ends, the cross-cutting coding rules, JSpecify-only nullness, and freedom from package cycles.
 * This suite is the harmonized counterpart of register's {@code ArchitectureTest} and despeckle's
 * {@code LayerDependencyTest}, parameterized for the {@code io.github.p4suta.tateyokopdf} package.
 *
 * <p>The Foreign Function &amp; Memory / method-handle confinement rules the sibling suites once
 * carried are absent here for the same reason they were dropped there: tate-yoko-pdf owns no FFM
 * island (no production class touches {@code java.lang.foreign} or {@code java.lang.invoke}), and
 * the shared unsafe binding lives in {@code :shared:imaging}, off this analyzed package set, where
 * {@code :shared:arch-rules} confines it.
 *
 * <p>Analyzed from {@code :app}, whose test classpath sees every module. The {@code testFixtures}
 * sourceSet is excluded ({@link NoTestFixtures}) so fixtures may use PDFBox directly to build PDFs
 * without tripping these rules. The class graph is imported once (production classes only) and
 * shared across every {@link ArchTest} rule below.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.tateyokopdf",
        importOptions = {
            ImportOption.DoNotIncludeTests.class,
            LayerDependencyTest.NoTestFixtures.class
        })
final class LayerDependencyTest {

    /**
     * Excludes the {@code testFixtures} sourceSet — fixtures may use PDFBox directly to build PDFs.
     */
    public static final class NoTestFixtures implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return !location.contains("testFixtures") && !location.contains("test-fixtures");
        }
    }

    /** Any nullness annotation ({@code @Nullable}/{@code @NonNull}/...) not from JSpecify. */
    private static final DescribedPredicate<JavaClass> NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY =
            simpleName("Nullable")
                    .or(simpleName("NonNull"))
                    .or(simpleName("Nonnull"))
                    .or(simpleName("NotNull"))
                    .and(not(resideInAPackage("org.jspecify.annotations")))
                    .as("a nullness annotation not from JSpecify");

    /**
     * The hexagonal graph: {@code domain} is the pure center; {@code port} sees only it; {@code
     * application} and {@code infrastructure} are the adapters/orchestration around the ports;
     * {@code app} (the {@code Main} entry point, the {@code cli} shell and the {@code tools} dev
     * utilities) is the composition root no layer may reach back into. The inter-module dependency
     * edges enforce most of this at compile time; this rule pins it within the single imported
     * graph and catches a violation the module classpaths cannot (e.g. {@code application} reaching
     * {@code infrastructure}).
     */
    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("io.github.p4suta.tateyokopdf.domain..")
                    .layer("Port")
                    .definedBy("io.github.p4suta.tateyokopdf.port..")
                    .layer("Application")
                    .definedBy("io.github.p4suta.tateyokopdf.application..")
                    .layer("Infrastructure")
                    .definedBy("io.github.p4suta.tateyokopdf.infrastructure..")
                    .layer("App")
                    .definedBy(
                            "io.github.p4suta.tateyokopdf",
                            "io.github.p4suta.tateyokopdf.cli..",
                            "io.github.p4suta.tateyokopdf.tools..")
                    .whereLayer("App")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("App")
                    .whereLayer("Infrastructure")
                    .mayOnlyBeAccessedByLayers("App")
                    .whereLayer("Port")
                    .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "App")
                    .whereLayer("Domain")
                    .mayOnlyBeAccessedByLayers("Port", "Application", "Infrastructure", "App");

    /** The domain is pure: it depends on no other layer of the codebase. */
    @ArchTest
    static final ArchRule domainIsPure =
            noClasses()
                    .that()
                    .resideInAPackage("..tateyokopdf.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..tateyokopdf.port..",
                            "..tateyokopdf.application..",
                            "..tateyokopdf.infrastructure..",
                            "..tateyokopdf.cli..");

    /** No package may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.tateyokopdf.(*)..").should().beFreeOfCycles();

    /**
     * The pure layers stay free of filesystem I/O. {@code java.nio.file.Path} is a value they may
     * name (it appears in {@code port} signatures and the {@code domain} naming helpers), but
     * {@code java.nio.file.Files} — the read/write helper — belongs to the adapters and the
     * orchestration. The module graph cannot enforce this (both live in {@code java.base}), so it
     * is pinned here, keeping the {@code domain}/{@code port} part of the original {@code
     * filesystemAccessConfined} guarantee alive now that the broad onion rule is gone.
     *
     * <p>One documented carve-out: {@code domain.exception.Validators#requireExists} does a single
     * {@code Files.exists} precondition check at the validation boundary (it converts a missing
     * input path into a typed {@link io.github.p4suta.tateyokopdf.domain.exception.SpreadException}
     * before any adapter runs). It is named explicitly so the rest of {@code domain}/{@code port}
     * stays pinned filesystem-free; everything else routes real read/write through the adapters.
     */
    @ArchTest
    static final ArchRule domainAndPortDoNotTouchTheFilesystem =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.p4suta.tateyokopdf.domain..",
                            "io.github.p4suta.tateyokopdf.port..")
                    .and()
                    .doNotHaveFullyQualifiedName(
                            "io.github.p4suta.tateyokopdf.domain.exception.Validators")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.file.Files");

    /**
     * Launching external processes is an adapter concern: the {@code infrastructure} qpdf/PDFBox
     * adapters shell out to the post-processor, and the app-side {@code tools} dev utilities
     * ({@code RuntimeBenchmark} times the packaged launcher, {@code SmokeCheck} drives the jpackage
     * image) spawn the built CLI to measure or sanity-check it. Both are legitimate
     * process-spawning sites; keep {@code ProcessBuilder} out of the pure {@code domain}/{@code
     * port}, the {@code application} orchestration and the {@code cli} shell.
     */
    @ArchTest
    static final ArchRule processBuilderConfinedToInfrastructure =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "..tateyokopdf.infrastructure..", "..tateyokopdf.tools..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.lang.ProcessBuilder");

    /**
     * Apache PDFBox is the one PDF toolkit, used only to read source pages and assemble the spread
     * output PDF in {@code infrastructure}. Confining it there keeps PDF concerns out of the domain
     * and the orchestration.
     */
    @ArchTest
    static final ArchRule pdfBoxConfinedToInfrastructure =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..tateyokopdf.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.pdfbox..");

    /**
     * Apache Commons CLI is the argument-parsing front end, confined to the {@code cli} shell:
     * everything below the shell handles failures through the shared error model, never the CLI
     * parser's {@code ParseException}.
     */
    @ArchTest
    static final ArchRule commonsCliConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..tateyokopdf.cli..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.commons.cli..");

    /** Logging goes through SLF4J, never {@code java.util.logging}. */
    @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /** No JodaTime — the JDK time APIs are the standard. */
    @ArchTest static final ArchRule noJodaTime = NO_CLASSES_SHOULD_USE_JODATIME;

    /**
     * Throw a meaningful exception type, never a bare {@code Exception}/{@code RuntimeException}.
     */
    @ArchTest
    static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    /**
     * Standard streams ({@code System.out}/{@code System.err}) are confined to the user-facing
     * front ends — the {@code cli} package and the {@code Main} entry point, which print help,
     * version and parse errors themselves (the front end is Apache Commons CLI) — and to the
     * standalone developer {@code main()} utilities that are console programs by nature: the
     * app-side {@code tools} package ({@code RuntimeBenchmark}, {@code SmokeCheck}) and the {@code
     * infrastructure} PDFBox sample generator ({@code
     * infrastructure.pdfbox.tools.SamplePdfGenerator}). Everything in the real request path below
     * the shell routes output and progress through SLF4J.
     */
    @ArchTest
    static final ArchRule noStandardStreams =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "..tateyokopdf.cli..",
                            "..tateyokopdf.tools..",
                            "..tateyokopdf.infrastructure.pdfbox.tools..")
                    .and()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.tateyokopdf.Main")
                    .should(ACCESS_STANDARD_STREAMS);

    /**
     * Nullability is spoken in exactly one vocabulary — JSpecify — because that is what NullAway
     * reads. A nullness annotation from any other library would silently fall outside the
     * null-safety gate, so this allow-lists JSpecify and rejects every other source, including ones
     * not yet on the classpath (an allow-list, not a denylist of known offenders).
     */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);

    /**
     * The pure hexagon center may consume only the shared kernel primitives (value types / error
     * model): {@code io.github.p4suta.shared.kernel}. The imaging, pdf, process, io, observability
     * and cli shared islands are adapter-side and must never be reached from {@code domain}/{@code
     * port}. Phase 3 moved those islands cross-app; this rule re-pins the displaced invariant that
     * they stay out of the pure center.
     */
    @ArchTest
    static final ArchRule domainAndPortDependOnlyOnSharedKernel =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.p4suta.tateyokopdf.domain..",
                            "io.github.p4suta.tateyokopdf.port..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.github.p4suta.shared.imaging..",
                            "io.github.p4suta.shared.pdf..",
                            "io.github.p4suta.shared.process..",
                            "io.github.p4suta.shared.io..",
                            "io.github.p4suta.shared.observability..",
                            "io.github.p4suta.shared.cli..")
                    .as(
                            "an app's domain/port may depend on io.github.p4suta.shared.kernel.."
                                    + " only, never on any other shared island")
                    .because(
                            "the pure hexagon center may consume only the shared kernel primitives"
                                    + " (value types/error model); imaging, pdf, process, io,"
                                    + " observability and cli are adapter-side islands");
}
