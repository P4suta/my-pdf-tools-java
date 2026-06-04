package io.github.p4suta.despeckle.architecture;

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
 * Pins the architectural boundaries the Gradle module graph cannot express on its own, harmonized
 * with register and tate-yoko-pdf to the shared app-scope rule set: the hexagonal layering ({@code
 * domain} &larr; {@code port} &larr; {@code application} / {@code infrastructure} &larr; {@code
 * app}), {@code ProcessBuilder} and Commons CLI confinement, the filesystem-freedom of the pure
 * layers, {@code System.out} confined to the CLI shells, the new {@code domain}/{@code port} &rarr;
 * {@code :shared:kernel}-only boundary, a single nullness vocabulary, and the cross-cutting coding
 * rules. The graph is imported once (production classes only).
 *
 * <p>Even where the inter-module {@code project()} edges already make a violation un-compilable,
 * the intra-graph rules below still earn their place: {@code layeredArchitectureIsRespected}
 * catches edges the module classpaths cannot (e.g. {@code application} reaching {@code
 * infrastructure}, or a layer reaching back into {@code app}/{@code cli}); {@code
 * processBuilderConfinedToInfrastructure} and {@code commonsCliConfinedToCli} pin live dependencies
 * ({@code java.lang.ProcessBuilder} in {@code infrastructure.process}/{@code
 * infrastructure.report}; {@code org.apache.commons.cli} in the {@code cli} shells) at the class
 * level; and {@code filesystemAccessConfined} is kept only in its narrowed {@code domain}/{@code
 * port} half ({@link #domainAndPortDoNotTouchTheFilesystem}), because the module graph leaves
 * {@code java.nio.file.Files} reachable everywhere (it is in {@code java.base}).
 *
 * <p>Two confinement rules from the canonical template are deliberately omitted because their
 * guarded type is absent from this app's graph: the {@code pdfBoxConfinedToInfrastructure} rule
 * (despeckle's {@code infrastructure.pdf} delegates to {@code io.github.p4suta.shared.pdf}; no
 * despeckle class imports {@code org.apache.pdfbox}, so the rule would be vacuous), and the Foreign
 * Function &amp; Memory / method-handle confinement rules (that unsafe island now lives in the
 * shared {@code :shared:imaging} module, off this analyzed package set, so no despeckle production
 * class touches {@code java.lang.foreign} or {@code java.lang.invoke}). The shared islands are
 * confined for the {@code io.github.p4suta.shared} graph by {@code :shared:arch-rules}.
 *
 * <p>Analyzed from {@code :app}, whose test classpath sees every module. The {@code testFixtures}
 * sourceSet of {@code :infrastructure} is excluded ({@link NoTestFixtures}) so fixtures may use
 * PDFBox directly to build PDFs without tripping these rules. The class graph is imported once
 * (production classes only) and shared across every {@link ArchTest} rule below.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.despeckle",
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
     * {@code app} (the {@code Main} entry point and the {@code cli} shells) is the composition root
     * no layer may reach back into. The inter-module dependency edges enforce most of this at
     * compile time; this rule pins it within the single imported graph and catches a violation the
     * module classpaths cannot (e.g. {@code application} reaching {@code infrastructure}).
     */
    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("io.github.p4suta.despeckle.domain..")
                    .layer("Port")
                    .definedBy("io.github.p4suta.despeckle.port..")
                    .layer("Application")
                    .definedBy("io.github.p4suta.despeckle.application..")
                    .layer("Infrastructure")
                    .definedBy("io.github.p4suta.despeckle.infrastructure..")
                    .layer("App")
                    .definedBy("io.github.p4suta.despeckle", "io.github.p4suta.despeckle.cli..")
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

    /** No package may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.despeckle.(*)..").should().beFreeOfCycles();

    /**
     * The pure layers stay free of filesystem I/O. {@code java.nio.file.Path} is a value they may
     * name (it appears in {@code port} signatures and the {@code domain} naming helpers), but
     * {@code java.nio.file.Files} — the read/write helper — belongs to the adapters and the
     * orchestration. The module graph cannot enforce this (both live in {@code java.base}), so it
     * is pinned here, keeping the {@code domain}/{@code port} part of the original {@code
     * filesystemAccessConfined} guarantee alive now that the broad onion rule is gone.
     */
    @ArchTest
    static final ArchRule domainAndPortDoNotTouchTheFilesystem =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.p4suta.despeckle.domain..",
                            "io.github.p4suta.despeckle.port..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.file.Files");

    /**
     * Launching external processes is an infrastructure concern: {@code infrastructure.report}
     * shells out to {@code img2webp}/{@code ffmpeg} for the WebP flip-book and {@code
     * infrastructure.process} drives the native CLI tools. Keep {@code ProcessBuilder} out of the
     * domain, the orchestration and the CLI.
     */
    @ArchTest
    static final ArchRule processBuilderConfinedToInfrastructure =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..despeckle.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.lang.ProcessBuilder");

    /**
     * Apache Commons CLI is the argument-parsing front end, confined to the {@code cli} shells:
     * everything below the shell handles failures through the shared error model, never the CLI
     * parser's {@code ParseException}.
     */
    @ArchTest
    static final ArchRule commonsCliConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..despeckle.cli..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.commons.cli..");

    /**
     * The pure hexagon center may consume only the shared kernel primitives (value types / error
     * model). The other shared islands — imaging, pdf, process, io, observability and cli — are
     * adapter-side, so a {@code domain}/{@code port} class that reached into one of them would be
     * pulling adapter machinery into the pure center. (The Phase-3 refactor moved FFM &rarr; {@code
     * shared.imaging}, PDFBox &rarr; {@code shared.pdf}, etc.; this is the displaced invariant that
     * those moves left without an enforcer.)
     */
    @ArchTest
    static final ArchRule domainAndPortDependOnlyOnSharedKernel =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.p4suta.despeckle.domain..",
                            "io.github.p4suta.despeckle.port..")
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

    // The Foreign Function & Memory / method-handle confinement rules that used to live here are
    // gone: despeckle no longer owns an FFM island. The unsafe binding (and the raw downcall
    // handles) moved to the shared :shared:imaging module, which is off this module graph's
    // analyzed package set (io.github.p4suta.despeckle), so no despeckle production class touches
    // java.lang.foreign or java.lang.invoke at all and the rules became vacuous. pdfBox confinement
    // is likewise omitted: despeckle's infrastructure.pdf delegates to io.github.p4suta.shared.pdf,
    // so no despeckle class imports org.apache.pdfbox and such a rule would be vacuous here too.

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
     * Only the CLI front end ({@code DespeckleCli}) may touch {@code System.out}/{@code
     * System.err}: it prints help, version and usage straight to the process streams like any
     * normal CLI, while every other layer routes user-facing output and progress through SLF4J.
     * (picocli used to hide this write inside its own library; Commons CLI hands it back to us, so
     * the carve-out is named.)
     */
    @ArchTest
    static final ArchRule noStandardStreams =
            noClasses()
                    .that()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.despeckle.cli.DespeckleCli")
                    .and()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.despeckle.cli.PipelineCli")
                    .and()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.despeckle.cli.TopdfCli")
                    .should(ACCESS_STANDARD_STREAMS)
                    .as(
                            "only the CLI front ends (DespeckleCli, PipelineCli, TopdfCli) may"
                                    + " access standard streams")
                    .because(
                            "help/version/usage go straight to the process streams like a normal"
                                    + " CLI; every other layer logs through SLF4J");

    /**
     * Nullability is spoken in exactly one vocabulary — JSpecify — because that is what NullAway
     * reads. A nullness annotation from any other library would silently fall outside the
     * null-safety gate, so this allow-lists JSpecify and rejects every other source, including ones
     * not yet on the classpath (an allow-list, not a denylist of known offenders).
     */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);
}
