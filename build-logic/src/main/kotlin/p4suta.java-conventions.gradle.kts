import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

// Shared Java compilation conventions for every production module across all three p4suta apps:
// the Java 25 toolchain, the Error Prone + NullAway zero-warning null-safety gate, the Javadoc
// doclint gate, and the Dependabot security-patch resolution strategy.
//
// Module-specific dependencies — including the SLF4J facade, which only the layers that log declare
// (so :domain and :port stay free of any third-party runtime) — live in each module's own build
// script. Native access is NOT granted here: a module opts in by also applying
// p4suta.native-conventions, the single place `--enable-native-access` is configured.
plugins {
    java
    id("net.ltgt.errorprone")
}

// Unique per-app group so each module's Gradle capability (group:name) is distinct across apps.
// Every app repeats the leaf module names domain/port/application/infrastructure, so register's
// :domain and pipeline's :domain are both Gradle-named "domain" — without a distinct group they
// collide on the single capability io.github.p4suta:domain the moment one classpath pulls both
// (e.g. :pipeline:infrastructure depends on every app's :domain). Deriving the group from the
// parent project (the app/shared directory) keeps coordinates unique. Modules are never published,
// so the group is internal-only.
group = "io.github.p4suta." + (project.parent?.name ?: project.name)
version = "0.1.0"

// Unique jar filename per module ("<app>-<layer>", e.g. register-application, pipeline-domain).
// Every app repeats the leaf names domain/port/application/infrastructure, so the default jar name
// "<leaf>-<version>.jar" collides when one distribution bundles several apps' modules — the
// pipeline's installDist pulls every app's :application, all otherwise named application-0.1.0.jar,
// which have DIFFERENT contents (so deduping by name would drop classes). A shadow jar that sets its
// own archiveBaseName (the apps' jpackage shadowJar) is unaffected.
extensions.configure<BasePluginExtension> {
    archivesName = (project.parent?.name ?: project.name) + "-" + project.name
}

// Precompiled script plugins get no type-safe `libs.` accessors; read the catalog directly via the
// VersionCatalogsExtension (wired into this included build by build-logic/settings.gradle.kts).
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    // FFM is final since JDK 22; 25 is the current LTS. If Error Prone ever lags a JDK, the floor
    // that still builds is 22 (FFM is preview on 21).
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JSpecify @Nullable: the vocabulary NullAway reads to learn what may be null. CLASS-retention,
    // so compile-only — it must not leak onto any runtime classpath or into the shaded jar.
    "compileOnly"(libs.findLibrary("jspecify").get())

    "errorprone"(libs.findLibrary("errorprone-core").get())
    // NullAway runs as an Error Prone plugin (same `errorprone` configuration); there is no separate
    // NullAway Gradle plugin, so it is a library on this config rather than a plugin marker.
    "errorprone"(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Pin the documented JDK 25 API surface.
    options.release = 25
    // Warnings are errors. We exclude only the "options" category: the "system modules path not
    // set" note is an environmental artifact of toolchain compilation, not a code-quality signal.
    // Every code warning (deprecation, unchecked, removal, ...) still fails the build.
    options.compilerArgs.addAll(listOf("-Xlint:all,-options", "-Werror"))
    // NullAway: a missing null check inside our own package is a build error. Maximally strict —
    // full JSpecify semantics (generics, type-use positions; the source is @NullMarked per
    // package-info), restrictive third-party annotations honored, Optional/OptionalInt emptiness
    // flow-checked, and every override re-checked against its supertype. CheckContracts/
    // AssertsEnabled are pre-enabled so future contracts/asserts are honored without a config change.
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Future-proof against generated sources (annotation processors): never analyze them.
        excludedPaths = ".*/build/generated/.*"
        check("NullAway", CheckSeverity.ERROR)
        // AnnotatedPackages is the required baseline (NullAway demands exactly one of
        // AnnotatedPackages or OnlyNullMarked). The single `io.github.p4suta` prefix covers every
        // current and future sub-package across all three apps (register, despeckle, tateyokopdf).
        // The @NullMarked package-info files are kept as in-source / IDE documentation.
        option("NullAway:AnnotatedPackages", "io.github.p4suta")
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:AcknowledgeRestrictiveAnnotations", "true")
        option("NullAway:CheckOptionalEmptiness", "true")
        // The codebase models nullable numerics as OptionalInt/Long/Double, which the emptiness
        // check ignores by default; name the primitive optionals so getAsInt() is flow-checked
        // (e.g. guarded by isPresent()) like java.util.Optional.get().
        option(
            "NullAway:CheckOptionalEmptinessCustomClasses",
            "java.util.OptionalInt,java.util.OptionalLong,java.util.OptionalDouble",
        )
        option("NullAway:CheckContracts", "true")
        option("NullAway:ExhaustiveOverride", "true")
        option("NullAway:AssertsEnabled", "true")
    }
}

// NullAway guards the tests too: they are @NullMarked, and the strict options above (JSpecifyMode,
// restrictive annotations, ...) are inherited from the shared JavaCompile block. The explicit
// ERROR severity keeps the gate hard even if a future change relaxes the default for test sources.
// HandleTestAssertionLibraries lets JUnit/Hamcrest/AssertJ assertions establish non-null facts when
// a test relies on them.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:HandleTestAssertionLibraries", "true")
    }
}

// Javadoc doclint: validate cross-references, HTML, and syntax of the Javadoc we ship. `-missing`
// keeps the gate on correctness of written docs rather than exhaustive coverage.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addBooleanOption("Werror", true)
    }
}

tasks.named("check") { dependsOn(tasks.named("javadoc")) }

// Dependabot security alerts: pin vulnerable transitive deps on every runtime/test configuration.
// The floors live in the catalog so the single source of truth covers them too. (Adopted from
// tate-yoko-pdf.)
val securityPatches =
    mapOf(
        "org.codehaus.plexus:plexus-utils" to
            libs.findVersion("plexus-utils").get().requiredVersion,
        "org.apache.logging.log4j:log4j-core" to
            libs.findVersion("log4j-core").get().requiredVersion,
    )

configurations.all {
    resolutionStrategy.eachDependency {
        securityPatches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
    }
}
