package io.github.p4suta.webapp.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Pins the web feature's architectural boundaries the Gradle module graph cannot express: Spring
 * stays in the {@code app} shell, the feature is decoupled from the pipeline's internals, one
 * nullness vocabulary, and no package cycles.
 */
@AnalyzeClasses(
        packages = "io.github.p4suta.webapp",
        importOptions = ImportOption.DoNotIncludeTests.class)
class WebappArchitectureTest {

    /** Any nullness annotation ({@code @Nullable}/{@code @NonNull}/...) not from JSpecify. */
    private static final DescribedPredicate<JavaClass> NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY =
            simpleName("Nullable")
                    .or(simpleName("NonNull"))
                    .or(simpleName("Nonnull"))
                    .or(simpleName("NotNull"))
                    .and(not(resideInAPackage("org.jspecify.annotations")))
                    .as("a nullness annotation not from JSpecify");

    /** Spring lives only in the app shell; the brains and adapters below stay framework-free. */
    @ArchTest
    static final ArchRule springIsConfinedToTheAppLayer =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "..webapp.domain..",
                            "..webapp.port..",
                            "..webapp.application..",
                            "..webapp.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.springframework..");

    /**
     * Micrometer and Actuator stay in the shell ({@link #springIsConfinedToTheAppLayer} covers the
     * {@code org.springframework.boot} actuator packages, but not {@code io.micrometer}). The
     * telemetry seam ({@code QueueStats}) is plain Java, so the core never sees a metrics type.
     */
    @ArchTest
    static final ArchRule observabilityIsConfinedToTheAppLayer =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "..webapp.domain..",
                            "..webapp.port..",
                            "..webapp.application..",
                            "..webapp.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.micrometer..",
                            "org.springframework.boot.actuate..",
                            "org.springframework.boot.health..");

    /**
     * The whole feature treats pdfbook as an external tool, so nothing in {@code webapp} ever
     * compiles against the pipeline's or tate's code.
     */
    @ArchTest
    static final ArchRule webappNeverDependsOnThePipeline =
            noClasses()
                    .that()
                    .resideInAPackage("io.github.p4suta.webapp..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.github.p4suta.pipeline..", "io.github.p4suta.tateyokopdf..");

    /** No package may sit in a dependency cycle with another. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching("io.github.p4suta.webapp.(*)..").should().beFreeOfCycles();

    /** Nullability is spoken in exactly one vocabulary — JSpecify — which NullAway reads. */
    @ArchTest
    static final ArchRule nullnessAnnotationsComeFromJSpecify =
            noClasses().should().dependOnClassesThat(NULLNESS_ANNOTATION_NOT_FROM_JSPECIFY);
}
