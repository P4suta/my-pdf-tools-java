import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.net.HttpURLConnection
import java.net.URI
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport
import org.gradle.testing.jacoco.tasks.JacocoReport

// =============================================================================
// my-pdf-tools-java — MONOREPO ROOT aggregator
// =============================================================================
// No production code lives at the root. Per-module Java/test/quality/native config is in the shared
// build-logic convention plugins (p4suta.*-conventions); each app's runnable artifact and jpackage
// distribution live in its own :<app>:app module. This root build owns only the CROSS-MODULE,
// cross-app tooling that spans all five features (register, despeckle, tate-yoko-pdf, pipeline,
// webapp):
//
//   * The whole-build JaCoCo coverage aggregation over every module (`just coverage`).
//   * ben-manes dependencyUpdates + checkExtraVersions (`just outdated`).
//   * OpenRewrite (advisory; rewrite.yml at the root, deliberately NOT wired into `build`).
//   * aggregateJavadoc for GitHub Pages.
//
// MANDATORY apply-false block: the shared Spotless/Error Prone/SpotBugs plugins are loaded ONCE here
// at the root scope so the convention plugins that apply them across all sibling modules share a
// single plugin classloader — and, critically, a single Spotless SpotlessTaskService. Without it a
// multi-module build fails with "Cannot set the value of task ':<m>:spotlessJava' property
// 'taskService'". (despeckle's pattern; register's root spotless{} configuration is deliberately NOT
// merged in — you cannot both `apply false` the plugin and configure its extension.)
// =============================================================================

// Apply security patches to the root buildscript (plugin) classpath so Dependabot alerts on
// transitive deps that only appear via Gradle plugins (e.g. plexus-utils / log4j-core) are resolved.
// Versions are the same manual floors the catalog tracks; the buildscript block cannot see the
// version catalog accessors, so the literals are repeated here and verified against the catalog by
// the checkExtraVersions task below.
buildscript {
    val patches =
        mapOf(
            "org.codehaus.plexus:plexus-utils" to "4.0.2",
            "org.apache.logging.log4j:log4j-core" to "2.26.0",
        )
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            patches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
        }
    }
}

plugins {
    base
    // ben-manes dependencyUpdates (`just outdated`). The catalog defines no alias for it, so the
    // marker version is inline here (the apps used the same 0.54.0).
    id("com.github.ben-manes.versions") version "0.54.0"
    // OpenRewrite (advisory). rewrite.yml at the repo root is auto-discovered; the recipe is run on
    // demand (`just rewrite`), never wired into `build`.
    alias(libs.plugins.rewrite)
    // Whole-build coverage: merges every module's JaCoCo exec data into one cross-module report
    // (`./gradlew testCodeCoverageReport` / `just coverage`). Unlike the per-module floors, this view
    // credits a class for coverage from ANY module's tests — so adapters exercised only by an app's
    // end-to-end pipeline tests in :<app>:app show as covered here even though :<app>:infrastructure's
    // own isolated report cannot see them. Deliberately not wired into `build` to keep that gate lean.
    `jacoco-report-aggregation`
    // Load Spotless/Error Prone/SpotBugs once at the root scope (apply false) so the convention
    // plugins that apply them across the 18 sibling modules share one plugin classloader and one
    // Spotless SpotlessTaskService. See the header note. (Catalog plugin aliases.)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotbugs) apply false
}

group = "io.github.p4suta"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Pin transitive deps on the root's OWN configurations (rewrite / ben-manes / jacocoAggregation) too.
// Sourced from the version catalog so the single source of truth covers the root build as well
// (matching p4suta.java-conventions, which reads the same floors via findVersion on the same keys).
val rootLibs =
    extensions
        .getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
        .named("libs")
val rootSecurityPatches =
    mapOf(
        "org.codehaus.plexus:plexus-utils" to
            rootLibs.findVersion("plexus-utils").get().requiredVersion,
        "org.apache.logging.log4j:log4j-core" to
            rootLibs.findVersion("log4j-core").get().requiredVersion,
    )

configurations.all {
    resolutionStrategy.eachDependency {
        rootSecurityPatches["${requested.group}:${requested.name}"]?.let { useVersion(it) }
    }
}

// -----------------------------------------------------------------------------
// OpenRewrite (advisory) — root rewrite.yml is auto-discovered. Applied at the root, it visits the
// source sets of every subproject across all five features. Deliberately NOT wired into `build`, so a
// recipe never blocks a commit. After rewriteRun, `just rewrite` runs spotlessApply so
// google-java-format re-imposes the AOSP layout.
// -----------------------------------------------------------------------------
rewrite {
    activeRecipe("io.github.p4suta.CuratedCleanup")
    // OpenRewrite's Kotlin support reformats the Gradle scripts in ways that fight Spotless/ktlint
    // (which own them), so keep rewrite off the build files. Both globs are needed: the NIO glob
    // matcher requires a separator, so "**/*.gradle.kts" alone misses the repo-root build.gradle.kts.
    exclusion("**/*.gradle.kts", "*.gradle.kts")
}

dependencies {
    // OpenRewrite recipe modules. Only rewriteRun/rewriteDryRun pull these in; they are not part of
    // the `build` graph, so a recipe never blocks a commit.
    rewrite(platform(libs.rewrite.recipe.bom))
    rewrite(libs.rewrite.static.analysis)
    rewrite(libs.rewrite.testing.frameworks)
    rewrite(libs.rewrite.logging.frameworks)
    rewrite(libs.rewrite.migrate.java)
    // Not in the recipe BOM, so version-pinned in the catalog; the BOM platform above still aligns
    // its transitive rewrite-core.
    rewrite(libs.rewrite.java.security)

    // Every production module across all five features feeds the aggregated coverage report: 5
    // features x 5 modules = 25, plus the eight PRODUCTION cross-app shared modules (:shared:kernel +
    // :shared:observability + :shared:imaging + :shared:cli + :shared:process + :shared:pdf +
    // :shared:io + :shared:progress) = 33.
    // The per-app observability modules were removed; the cross-cutting
    // mapper/sanitizer/handler/exit-codes now live in :shared:observability, the two duplicated
    // Leptonica FFM islands now live in :shared:imaging, the app-layer CLI scaffolding (input/output
    // resolution, the stdin/stdout bridge, the batch driver, the exception handler, the parse
    // helpers) now lives in :shared:cli, the external-process plumbing (ToolPath / ProcessRunner /
    // Tasks) now lives in :shared:process, the PDF I/O adapters (the JBIG2 assembler, the pdfimages
    // extractor, the pure listing parser, the qpdf runner) now live in :shared:pdf, and the
    // byte-identical filesystem-orchestration helpers (prepare-output-dir, collect-files,
    // mirror-destination) now live in :shared:io. Module project paths are nested (:<app>:<module>)
    // under the unified root settings.
    //
    // DELIBERATELY EXCLUDED: :shared:arch-rules. It is a TEST-ONLY module (a single ArchUnit suite
    // over io.github.p4suta.shared, no main sources), so it has no production bytecode to cover and no
    // JaCoCo floor of its own — adding it to the aggregation would contribute zero coverable classes.
    // Hence there is no jacocoAggregation(project(":shared:arch-rules")) line below.
    val aggregatedApps = listOf("register", "despeckle", "tate-yoko-pdf", "pipeline", "webapp")
    val aggregatedModules =
        listOf("domain", "port", "application", "infrastructure", "app")
    aggregatedApps.forEach { app ->
        aggregatedModules.forEach { module ->
            jacocoAggregation(project(":$app:$module"))
        }
    }
    jacocoAggregation(project(":shared:kernel"))
    jacocoAggregation(project(":shared:observability"))
    jacocoAggregation(project(":shared:imaging"))
    jacocoAggregation(project(":shared:cli"))
    jacocoAggregation(project(":shared:process"))
    jacocoAggregation(project(":shared:pdf"))
    jacocoAggregation(project(":shared:io"))
    jacocoAggregation(project(":shared:progress"))
}

// Register the aggregated report over every module's `test` suite. Produces the
// `testCodeCoverageReport` task (build/reports/jacoco/testCodeCoverageReport/, HTML to browse + XML
// for tooling such as the `just coverage` summary).
reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

// Emit XML + CSV beside the HTML so the `just coverage` summary reads a stable machine format
// instead of anyone hand-parsing reports.
tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required = true
        csv.required = true
        html.required = true
    }
}

// -----------------------------------------------------------------------------
// aggregateJavadoc — Javadoc for GitHub Pages (despeckle's pattern, extended to all five features).
// The root has no sources, so each module writes its own build/docs/javadoc; this collects them
// under build/docs/javadoc/<app>/<module> (the path the docs workflow uploads) behind a small
// landing index. A Copy — not a cross-project Javadoc task — keeps it configuration-cache-safe: it
// only reads each module's javadoc *output* by relative path and depends on that module's javadoc
// task. The app/module lists and output dir are captured as task-local vals (not script-top-level
// fields) so the doLast does not pull a script reference into the configuration cache.
// -----------------------------------------------------------------------------
tasks.register<Copy>("aggregateJavadoc") {
    group = "documentation"
    description = "Collect every module's Javadoc under build/docs/javadoc for GitHub Pages."
    // Per-app modules (the per-app :observability modules were removed; the cross-cutting
    // mapper/handler/exit-codes now live in :shared:observability) + the shared library modules.
    val appModules = listOf("domain", "port", "application", "infrastructure", "app")
    val sharedModules =
        listOf("kernel", "observability", "imaging", "cli", "process", "pdf", "io", "progress")
    val outDir = layout.buildDirectory.dir("docs/javadoc")
    val coordinates =
        listOf("register", "despeckle", "tate-yoko-pdf", "pipeline", "webapp")
            .flatMap { app -> appModules.map { module -> app to module } } +
            sharedModules.map { module -> "shared" to module }
    coordinates.forEach { (app, module) ->
        dependsOn(":$app:$module:javadoc")
        from("$app/$module/build/docs/javadoc") { into("$app/$module") }
    }
    into(outDir)
    doLast {
        val links =
            coordinates.joinToString("\n") { (app, module) ->
                "  <li><a href=\"$app/$module/index.html\"><code>:$app:$module</code></a></li>"
            }
        outDir
            .get()
            .asFile
            .resolve("index.html")
            .writeText(
                """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>my-pdf-tools-java &mdash; API docs</title></head>
                <body>
                <h1>my-pdf-tools-java &mdash; module API docs</h1>
                <ul>
                $links
                </ul>
                </body>
                </html>
                """.trimIndent() + "\n",
            )
    }
}

// =============================================================================
// `just outdated` plumbing (adopted from tate-yoko-pdf, RE-POINTED to the monorepo layout)
// -----------------------------------------------------------------------------
// 1. ben-manes.versions: only show stable upgrades (skip alpha/beta/rc/M*/SNAPSHOT).
// 2. checkExtraVersions: diff the non-Gradle pins (Dockerfile ARGs, plus the versions the shared
//    p4suta.* convention plugins now read from the catalog) against upstream stable releases via
//    GitHub Releases + Maven Central.
//
//    LAYOUT CHANGE vs tate-yoko-pdf's original: the unified convention plugins
//    (build-logic/src/main/kotlin/p4suta.{java,test,quality,native}-conventions.gradle.kts) no longer
//    inline version literals — they resolve every version through `libs.findVersion(...)`. So the
//    google-java-format / jacoco / security-patch CURRENT versions now live ONLY in
//    gradle/libs.versions.toml, and the regex reads point THERE. (The convention-plugin paths are
//    still captured below as the canonical home of that config, and to fail loudly if they vanish,
//    but the version literals are extracted from the catalog.) The Maven/GitHub coordinates for the
//    upstream lookups are unchanged.
// =============================================================================

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isNonStable(candidate.version) }
    outputFormatter = "plain"
    checkForGradleUpdate = true
    finalizedBy("checkExtraVersions")
    // ben-manes 0.54 uses Task.project / non-serializable lambdas, so `just outdated` is invoked with
    // --no-configuration-cache.
}

tasks.register("checkExtraVersions") {
    group = "help"
    description = "Diff non-Gradle pinned versions against upstream stable releases"
    // Capture every file and provider at configuration time so doLast holds no Project reference.
    val dockerfile = rootProject.file("Dockerfile")
    // The version catalog is the single source of truth for the versions the convention plugins
    // resolve via findVersion(...), so the "current" reads come from here.
    val versionCatalog = rootProject.file("gradle/libs.versions.toml")
    // This root build also repeats the security-patch floors as buildscript-classpath literals (the
    // buildscript block cannot see the catalog accessors). Capture it so the security-pin check can
    // diff those literals against the catalog and flag drift (the extra `"g:a" to "x"` form).
    val buildScript = rootProject.file("build.gradle.kts")
    // Convention plugins under the NEW layout. They no longer hold version literals (they read from
    // the catalog), but capturing them asserts the expected files exist — a loud failure if the
    // build-logic layout is ever moved out from under this task.
    val javaConventions =
        rootProject.file("build-logic/src/main/kotlin/p4suta.java-conventions.gradle.kts")
    val testConventions =
        rootProject.file("build-logic/src/main/kotlin/p4suta.test-conventions.gradle.kts")
    val qualityConventions =
        rootProject.file("build-logic/src/main/kotlin/p4suta.quality-conventions.gradle.kts")
    val nativeConventions =
        rootProject.file("build-logic/src/main/kotlin/p4suta.native-conventions.gradle.kts")
    // Resolve the workflow file list at configuration time (rootProject.fileTree inside doLast is a
    // configuration-cache leak).
    val workflowFiles =
        rootProject
            .fileTree(".github/workflows") {
                include("*.yml", "*.yaml")
            }.files
    val failOnUpdates =
        providers.gradleProperty("failOnUpdates").map { it.toBoolean() }.getOrElse(false)
    outputs.upToDateWhen { false }

    doLast {
        val dockerfileText = dockerfile.readText()
        val catalogText = versionCatalog.readText()
        val buildScriptText = buildScript.readText()
        // Read the convention-plugin files purely to assert the expected layout is present; their
        // versions live in the catalog now, so nothing version-shaped is extracted from them.
        listOf(javaConventions, testConventions, qualityConventions, nativeConventions).forEach {
            require(it.isFile) { "expected convention plugin missing: ${it.path}" }
        }
        val knownDockerArgs =
            setOf(
                "TYPOS_VERSION",
                "JUST_VERSION",
                "LEFTHOOK_VERSION",
                "TAPLO_VERSION",
                "BIOME_VERSION",
                "YAMLFMT_VERSION",
                "ACTIONLINT_VERSION",
                "NODE_VERSION",
                "PNPM_VERSION",
            )

        val stableRe = Regex("^[0-9]+(\\.[0-9]+)*$")

        fun parseVersion(v: String): List<Int> = v.split(".").map { it.toIntOrNull() ?: 0 }

        val versionComparator =
            Comparator<String> { a, b ->
                val pa = parseVersion(a)
                val pb = parseVersion(b)
                (0 until maxOf(pa.size, pb.size))
                    .map { pa.getOrElse(it) { 0 }.compareTo(pb.getOrElse(it) { 0 }) }
                    .firstOrNull { it != 0 } ?: 0
            }

        fun fetch(url: String): String? {
            repeat(3) { attempt ->
                val result =
                    runCatching {
                        (URI(url).toURL().openConnection() as HttpURLConnection).run {
                            connectTimeout = 10_000
                            readTimeout = 10_000
                            setRequestProperty("User-Agent", "my-pdf-tools-java-checkExtraVersions")
                            setRequestProperty("Accept", "application/json")
                            if (responseCode in 200..299) {
                                inputStream.use { it.bufferedReader().readText() }
                            } else {
                                null
                            }
                        }
                    }.getOrNull()
                if (result != null) return result
                if (attempt < 2) Thread.sleep(500L * (attempt + 1))
            }
            return null
        }

        fun latestGitHub(repo: String): String? {
            val body = fetch("https://api.github.com/repos/$repo/releases/latest") ?: return null
            val tag =
                Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)
                    ?.groupValues
                    ?.get(1) ?: return null
            return tag.removePrefix("v")
        }

        // Latest Active LTS from the Node release index (entries whose "lts" is a codename string,
        // not false). A build toolchain should track LTS, not the Current line.
        fun latestNodeLts(): String? {
            val body = fetch("https://nodejs.org/dist/index.json") ?: return null
            return Regex("\"version\"\\s*:\\s*\"v([^\"]+)\"[^}]*?\"lts\"\\s*:\\s*\"[^\"]+\"")
                .findAll(body)
                .map { it.groupValues[1] }
                .filter { stableRe.matches(it) }
                .maxWithOrNull(versionComparator)
        }

        fun latestMaven(
            group: String,
            artifact: String,
        ): String? {
            val url = "https://search.maven.org/solrsearch/select?q=g:%22$group%22+AND+a:%22$artifact%22&core=gav&rows=200&wt=json"
            val body = fetch(url) ?: return null
            return Regex("\"v\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(body)
                .map { it.groupValues[1] }
                .filter { stableRe.matches(it) }
                .maxWithOrNull(versionComparator)
        }

        fun dockerArg(name: String): String =
            Regex("^ARG ${Regex.escape(name)}=(\\S+)", RegexOption.MULTILINE)
                .find(dockerfileText)
                ?.groupValues
                ?.get(1)
                ?: error("Dockerfile is missing ARG $name=")

        // Search a given text for a version-shaped capture. Finds all occurrences so a duplicated pin
        // is detected when copies drift apart.
        fun extractFrom(
            text: String,
            pattern: String,
        ): String {
            val matches =
                Regex(pattern)
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .filter { stableRe.matches(it) }
                    .toList()
            return when {
                matches.isEmpty() -> error("expected /$pattern/ not found")
                matches.distinct().size == 1 -> matches.first()
                else -> matches.joinToString("/") // visual mismatch flag in the report
            }
        }

        // Read a [versions] leaf from the catalog TOML: `key = "x.y.z"`, line-anchored so a key is
        // not matched as a substring of a longer key.
        fun catalogVersion(key: String): String =
            extractFrom(catalogText, """(?m)^${Regex.escape(key)}\s*=\s*"([^"]+)"""")

        var updates = 0
        var headCount = 0

        fun report(
            name: String,
            current: String,
            latest: String?,
        ) {
            val tag =
                when {
                    latest == null -> {
                        "ERR "
                    }

                    current == latest -> {
                        "OK  "
                    }

                    versionComparator.compare(current, latest) > 0 -> {
                        headCount++
                        "HEAD"
                    }

                    else -> {
                        updates++
                        "UPD "
                    }
                }
            println("[%s] %-22s current=%-12s latest=%s".format(tag, name, current, latest ?: "(fetch failed)"))
        }

        println()
        println("=== Extra pinned versions (non-Gradle; current read from gradle/libs.versions.toml) ===")
        report("typos", dockerArg("TYPOS_VERSION"), latestGitHub("crate-ci/typos"))
        report("just", dockerArg("JUST_VERSION"), latestGitHub("casey/just"))
        // The SPA toolchain baked into the dev image. node tracks the latest Active LTS (a build
        // toolchain should ride LTS, not the Current line); pnpm must equal the frontend's
        // packageManager field.
        report("node", dockerArg("NODE_VERSION"), latestNodeLts())
        report("pnpm", dockerArg("PNPM_VERSION"), latestGitHub("pnpm/pnpm"))
        // google-java-format: the unified p4suta.quality-conventions reads it via
        // findVersion("google-java-format"); the literal lives in the catalog.
        report(
            "google-java-format",
            catalogVersion("google-java-format"),
            latestGitHub("google/google-java-format"),
        )
        // jacoco toolVersion: p4suta.test-conventions reads findVersion("jacoco"). (spotbugs'
        // toolVersion is read from the catalog too but is intentionally not tracked here.)
        report(
            "jacoco",
            catalogVersion("jacoco"),
            latestMaven("org.jacoco", "jacoco"),
        )

        println("--- security-patch pins (manual floors; bump when upstream catches up) ---")
        // The floors live in the catalog ([versions] plexus-utils / log4j-core) and are read from
        // there by p4suta.java-conventions (the source of truth). This root build ALSO repeats them
        // as buildscript-classpath `"g:a" to "x"` literals (the buildscript block cannot see the
        // catalog accessors). Extract from BOTH the catalog leaf and the buildscript map; extractFrom
        // joins divergent values with "/", so the report visibly flags drift before comparing to
        // upstream.
        val securityPins =
            listOf(
                Triple("plexus-utils", "org.codehaus.plexus", "plexus-utils"),
                Triple("log4j-core", "org.apache.logging.log4j", "log4j-core"),
            )
        for ((catalogKey, g, a) in securityPins) {
            val catalogPin = catalogVersion(catalogKey)
            val buildscriptPin =
                extractFrom(buildScriptText, """"${Regex.escape("$g:$a")}"\s+to\s+"([^"]+)"""")
            // Surface drift between the catalog floor and the buildscript literal as a "/"-joined
            // current; report() compares it to the latest upstream stable release.
            val current =
                if (catalogPin == buildscriptPin) catalogPin else "$catalogPin/$buildscriptPin"
            report(catalogKey, current, latestMaven(g, a))
        }

        // --- GitHub Actions (.github/workflows/*) ---------------------------------
        println("--- GitHub Actions ---")
        val actionUses = mutableMapOf<String, String>()
        val branchPins = mutableSetOf<String>()
        for (yml in workflowFiles) {
            Regex("""uses:\s*([\w./-]+)@(\S+)""")
                .findAll(yml.readText())
                .forEach { m ->
                    val ref = m.groupValues[1]
                    val ver = m.groupValues[2]
                    if (ver == "master" || ver == "main") {
                        branchPins.add(ref)
                    } else {
                        actionUses[ref] = ver
                    }
                }
        }

        fun majorOf(v: String): Int =
            v
                .removePrefix("v")
                .split(".")
                .firstOrNull()
                ?.toIntOrNull() ?: 0

        for ((ref, currentVer) in actionUses.toSortedMap()) {
            val parts = ref.split("/")
            val repo = if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ref
            val latestTag =
                fetch("https://api.github.com/repos/$repo/releases/latest")
                    ?.let {
                        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                            .find(it)
                            ?.groupValues
                            ?.get(1)
                    }
            val currentMajor = majorOf(currentVer)
            val latestMajor = latestTag?.let { majorOf(it) }
            val tag =
                when {
                    latestTag == null -> {
                        "ERR "
                    }

                    latestMajor == currentMajor -> {
                        "OK  "
                    }

                    latestMajor!! > currentMajor -> {
                        updates++
                        "UPD "
                    }

                    else -> {
                        headCount++
                        "HEAD"
                    }
                }
            println("[%s] %-36s current=%-12s latest=%s".format(tag, ref, currentVer, latestTag ?: "(fetch failed)"))
        }
        for (ref in branchPins.toSortedSet()) {
            println("[INFO] %-36s pinned to branch (not version-tracked)".format(ref))
        }

        val unknownDockerArgs =
            Regex("^ARG ([A-Z_]+_VERSION)=", RegexOption.MULTILINE)
                .findAll(dockerfileText)
                .map { it.groupValues[1] }
                .filter { it !in knownDockerArgs }
                .toList()
        if (unknownDockerArgs.isNotEmpty()) {
            println()
            for (arg in unknownDockerArgs) {
                println("WARN: unknown pinned version in Dockerfile: ARG $arg=… (add to knownDockerArgs in build.gradle.kts)")
            }
        }

        println()
        println("$updates update(s) available")

        val totalProblems = updates + headCount + unknownDockerArgs.size
        if (failOnUpdates && totalProblems > 0) {
            throw GradleException(
                "$totalProblems pin(s) need attention (updates=$updates, head=$headCount, unknown=${unknownDockerArgs.size}). " +
                    "Re-run without -PfailOnUpdates=true to see the report and resolve.",
            )
        }
    }
}
// ---- end `just outdated` plumbing -------------------------------------------
