import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.testing.jacoco.tasks.JacocoReport

// Shared test stack and execution settings — the superset across all three p4suta apps:
// JUnit 5 + ArchUnit + jqwik engines, with AssertJ / Mockito / EqualsVerifier / system-stubs /
// awaitility available, the SLF4J binding for test output, and JaCoCo report wiring.
//
// Native access for FFM tests is NOT configured here: a module that needs it applies
// p4suta.native-conventions, which adds `--enable-native-access` and `-Xshare:off` to the Test JVM.
// This convention owns only the test-framework JVM args (`--add-opens` for system-stubs) plus its
// own `-Xshare:off` for the jacoco-agent CDS warning, which fires regardless of native access.
//
// Per-module coverage thresholds and pitest config live in each module's own build script.
plugins {
    java
    jacoco
}

// Precompiled script plugins get no type-safe `libs.` accessors; read the catalog directly.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("archunit-junit5").get())
    "testImplementation"(libs.findLibrary("assertj-core").get())
    "testImplementation"(libs.findLibrary("mockito-core").get())
    "testImplementation"(libs.findLibrary("mockito-junit-jupiter").get())
    "testImplementation"(libs.findLibrary("jqwik").get())
    "testImplementation"(libs.findLibrary("equalsverifier").get())
    "testImplementation"(libs.findLibrary("system-stubs-jupiter").get())
    "testImplementation"(libs.findLibrary("awaitility").get())
    "testCompileOnly"(libs.findLibrary("jspecify").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    "testRuntimeOnly"(libs.findLibrary("slf4j-simple").get())
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik", "archunit")
    }
    finalizedBy(tasks.named("jacocoTestReport"))
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // system-stubs needs reflective access to mutate env vars on JDK 17+. -Xshare:off silences the
    // JVM CDS warning that fires when jacoco's javaagent appends to the bootstrap classpath.
    // (Native access, where a module needs it, comes from p4suta.native-conventions.)
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-Xshare:off",
    )
    testLogging {
        events("failed")
        showStackTraces = true
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}
