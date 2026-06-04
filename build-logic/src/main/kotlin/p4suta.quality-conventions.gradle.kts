import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// Shared formatting and bytecode-analysis conventions across all three p4suta apps: Spotless
// (google-java-format, AOSP style) and SpotBugs at MAX effort / MEDIUM confidence, sharing the one
// exclude filter at the repository root (config/spotbugs/exclude.xml).
plugins {
    java
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

// Precompiled script plugins get no type-safe `libs.` accessors; read the catalog directly.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

spotless {
    java {
        target("src/**/*.java")
        targetExclude("build/**", "**/generated/**")
        // AOSP style = 4-space indent, 100-column, matching .editorconfig. reflowLongStrings folds
        // string concatenations back to the column limit.
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
            .aosp()
            .reflowLongStrings()
        // formatAnnotations keeps type-use / parameter annotations on the same line as the element
        // they annotate. It is a method on the `java { }` extension (NOT on GoogleJavaFormatConfig),
        // so it is a separate statement rather than a chained call.
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

spotbugs {
    // Analysis-engine version, sourced from the catalog (distinct from the Gradle plugin marker).
    toolVersion = libs.findVersion("spotbugs-tool").get().requiredVersion
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    // A finding fails the build (no silent reports), and stack traces aid triage when analysis
    // itself errors.
    ignoreFailures = false
    showStackTraces = true
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}

// Limit SpotBugs to production code: test / fixture code uses Mockito and assertion patterns that
// generate noisy false positives. The fixtures task only exists where java-test-fixtures applies,
// so match by name rather than named(...) to stay no-op where absent.
tasks
    .matching { it.name == "spotbugsTest" || it.name == "spotbugsTestFixtures" }
    .configureEach { enabled = false }

tasks.withType<SpotBugsTask>().configureEach {
    reports {
        create("html") { required.set(true) }
        // XML is the machine-readable format CI / dashboards consume.
        create("xml") { required.set(true) }
    }
}
