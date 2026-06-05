package io.github.p4suta.pipeline;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Pins the pipeline's architectural boundaries the Gradle module graph cannot express on its own:
 * the hexagonal layering ({@code domain} &larr; {@code port} &larr; {@code application} / {@code
 * infrastructure} &larr; {@code app}), the pure center's freedom from the filesystem, Commons CLI
 * and standard streams confined to the CLI shell, no package cycles, and one nullness vocabulary.
 *
 * <p>The pipeline's infrastructure deliberately depends across the three apps (despeckle / register
 * / tate) and the shared islands — that is the whole point of the orchestrator — so those edges are
 * outside this graph (which imports only {@code io.github.p4suta.pipeline}); the layered rule
 * considers only dependencies among the pipeline's own layers.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.pipeline",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

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
     * application} (the runner) and {@code infrastructure} (the adapters) sit around the ports;
     * {@code app} (the {@code Main} entry point and the {@code cli} shell) is the composition root
     * no layer may reach back into.
     */
    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("io.github.p4suta.pipeline.domain..")
                    .layer("Port")
                    .definedBy("io.github.p4suta.pipeline.port..")
                    .layer("Application")
                    .definedBy("io.github.p4suta.pipeline.application..")
                    .layer("Infrastructure")
                    .definedBy("io.github.p4suta.pipeline.infrastructure..")
                    .layer("App")
                    .definedBy("io.github.p4suta.pipeline", "io.github.p4suta.pipeline.cli..")
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

    /** The domain is pure: it depends on no other layer of the pipeline. */
    @ArchTest
    static final ArchRule domainIsPure =
            noClasses()
                    .that()
                    .resideInAPackage("..pipeline.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..pipeline.port..",
                            "..pipeline.application..",
                            "..pipeline.infrastructure..",
                            "..pipeline.cli..");

    /** No package may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.pipeline.(*)..").should().beFreeOfCycles();

    /**
     * The pure {@code domain} and the {@code port} contracts never touch the filesystem directly:
     * {@code java.nio.file.Files} belongs to the adapters and the orchestration.
     */
    @ArchTest
    static final ArchRule domainAndPortDoNotTouchTheFilesystem =
            noClasses()
                    .that()
                    .resideInAnyPackage("..pipeline.domain..", "..pipeline.port..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.file.Files");

    /**
     * Apache Commons CLI is the argument-parsing front end, confined to the {@code cli} shell;
     * everything below handles failures through the shared error model.
     */
    @ArchTest
    static final ArchRule commonsCliConfinedToCli =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..pipeline.cli..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.commons.cli..");

    /**
     * Standard streams ({@code System.out}/{@code System.err}) are confined to the CLI shell — the
     * {@code cli} package and the {@code Main} entry point. Everything below routes through SLF4J.
     */
    @ArchTest
    static final ArchRule noStandardStreams =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..pipeline.cli..")
                    .and()
                    .doNotHaveFullyQualifiedName("io.github.p4suta.pipeline.Main")
                    .should(ACCESS_STANDARD_STREAMS);

    /** Logging goes through SLF4J, never {@code java.util.logging}. */
    @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /**
     * Throw a meaningful exception type, never a bare {@code Exception}/{@code RuntimeException}.
     */
    @ArchTest
    static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    /** Nullability is spoken in exactly one vocabulary — JSpecify — which NullAway reads. */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);
}
