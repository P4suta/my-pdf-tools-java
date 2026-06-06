package io.github.p4suta.register;

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
 * Pins the architectural boundaries the Gradle module graph cannot express on its own: the
 * hexagonal layering ({@code domain} &larr; {@code port} &larr; {@code application} / {@code
 * infrastructure} &larr; {@code app}), PDFBox / {@code ProcessBuilder} / filesystem / {@code
 * System.out} confinement, Commons CLI pinned to the CLI shell, the pure center's dependence on the
 * shared kernel island only, and a single nullness vocabulary. The graph is imported once
 * (production classes only).
 *
 * <p>The FFM/Leptonica binding and the observability layer live in cross-app shared modules
 * (outside the {@code io.github.p4suta.register} package this graph imports), so there is no
 * in-graph FFM or observability surface to confine here.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.register",
        importOptions = {
            ImportOption.DoNotIncludeTests.class,
            ArchitectureTest.ExcludeTestFixtures.class
        })
class ArchitectureTest {

    /**
     * Drops the {@code testFixtures} source set from the analyzed graph. {@code :infrastructure}'s
     * test fixtures (the {@code TestImages} PBM builder) ride on {@code :app}'s test classpath and
     * touch {@code java.nio.file.Files}; the import filter keeps them out of the analyzed graph.
     * {@link ImportOption.DoNotIncludeTests} only filters the {@code test} output, not {@code
     * testFixtures}.
     */
    static final class ExcludeTestFixtures implements ImportOption {
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
     * {@code app} (the {@code Main} entry point and the {@code cli} shell) is the composition root
     * no layer may reach back into. The inter-module dependency edges enforce most of this at
     * compile time; this rule pins it within the single imported graph and catches a violation the
     * module classpaths cannot (e.g. {@code application} reaching {@code infrastructure}).
     */
    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("io.github.p4suta.register.domain..")
                    .layer("Port")
                    .definedBy("io.github.p4suta.register.port..")
                    .layer("Application")
                    .definedBy("io.github.p4suta.register.application..")
                    .layer("Infrastructure")
                    .definedBy("io.github.p4suta.register.infrastructure..")
                    .layer("App")
                    .definedBy("io.github.p4suta.register", "io.github.p4suta.register.cli..")
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
                    .resideInAPackage("..register.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..register.port..",
                            "..register.application..",
                            "..register.infrastructure..",
                            "..register.cli..");

    /** No package may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.register.(*)..").should().beFreeOfCycles();

    // No class under io.github.p4suta.register touches java.lang.foreign or java.lang.invoke: the
    // Leptonica binding lives in the cross-app io.github.p4suta.shared.imaging module, where FFM
    // confinement is enforced by :shared:arch-rules. Hence no FFM-confinement rule here.

    /**
     * The pure {@code domain} and the {@code port} contracts never touch the filesystem directly:
     * {@code java.nio.file.Files} (the filesystem read/write helper) belongs to the adapters and
     * the orchestration ({@code infrastructure}, {@code application}). This is the canonical
     * narrowed form shared across all three apps — it guards the pure hexagon center by package
     * name only, without naming the app-specific adapter layers.
     */
    @ArchTest
    static final ArchRule domainAndPortDoNotTouchTheFilesystem =
            noClasses()
                    .that()
                    .resideInAnyPackage("..register.domain..", "..register.port..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.file.Files");

    /**
     * The pure hexagon center may consume only the shared kernel primitives (value types / error
     * model): {@code domain} and {@code port} depend on {@code io.github.p4suta.shared.kernel} and
     * nothing else under {@code io.github.p4suta.shared}. The {@code imaging}, {@code pdf}, {@code
     * process}, {@code io}, {@code observability} and {@code cli} shared islands are adapter-side,
     * pinned out of the center.
     */
    @ArchTest
    static final ArchRule domainAndPortDependOnlyOnSharedKernel =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.p4suta.register.domain..",
                            "io.github.p4suta.register.port..")
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

    /**
     * Apache PDFBox is the one PDF toolkit, confined to {@code infrastructure}. The rule is vacuous
     * for register (JBIG2 assembly and CLI extraction delegate to {@code
     * io.github.p4suta.shared.pdf}, so no register class imports {@code org.apache.pdfbox}); kept
     * as a regression guard and to stay shape-identical with the cross-app suite, where it is live
     * for the tate-yoko app.
     */
    @ArchTest
    static final ArchRule pdfBoxConfinedToInfrastructure =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..register.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.pdfbox..");

    /**
     * Launching external processes is an infrastructure concern: {@code diag} shells out to {@code
     * img2webp} for the flip-book and the {@code process} helpers to {@code pdfimages}/{@code
     * jbig2}/{@code pdfinfo}. Keep {@code ProcessBuilder} out of the domain, the orchestration and
     * the CLI.
     */
    @ArchTest
    static final ArchRule processBuilderConfinedToInfrastructure =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..register.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.lang.ProcessBuilder");

    /**
     * Apache Commons CLI is the argument-parsing front end, confined to the {@code cli} shell:
     * everything below the shell handles failures through the shared error model, never the CLI
     * parser's {@code ParseException}.
     */
    @ArchTest
    static final ArchRule commonsCliConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..register.cli..")
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
     * Standard streams ({@code System.out}/{@code System.err}) are confined to the CLI shell — the
     * {@code cli} package and the {@code Main} entry point — which must print help, version and
     * parse errors itself (the front end is Apache Commons CLI). Everything below the shell routes
     * output and progress through SLF4J.
     */
    @ArchTest
    static final ArchRule noStandardStreams =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..register.cli..")
                    .and()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.register.Main")
                    .should(ACCESS_STANDARD_STREAMS);

    /**
     * Nullability is spoken in exactly one vocabulary — JSpecify — because that is what NullAway
     * reads.
     */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);
}
