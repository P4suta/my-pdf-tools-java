import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The eighth cross-app shared module: the filesystem-orchestration island. It generalizes the
// byte-identical helpers register's RegistrationService and despeckle's DespeckleService each
// re-derived (the Phase-4 review flagged them O2/DUP2 — byte-identical across the two apps):
//
//   * OutputDirs.prepare — ensure the output directory exists, rejecting a non-empty one unless
//     `force` (preserving the EXACT message the apps' E2E + unit tests assert:
//     `output directory <dir> is not empty; pass --force to overwrite`).
//   * CorpusFiles.collect — every regular file under a root whose NAME matches a caller-supplied
//     glob, sorted by full path string (`sorted(Comparator.comparing(Path::toString))`) so the
//     deterministic processing order the apps depend on is preserved. The glob is a PARAMETER, never
//     a hardcoded app extension set.
//   * CorpusFiles.mirrorDestination — the input-relative path of a source resolved under an output
//     root, with the extension swapped to a caller-supplied one (or kept when it is null, i.e.
//     `--format same`). The replacement extension is a `@Nullable String` PARAMETER — each app
//     passes its own `OutputFormat.extension()`, so this module imports neither app's enum.
//
// These are pure-ish java.nio.file.* operations; the only public-signature dependency is the JDK
// (Path / IOException). The SLF4J facade is the single declared dependency to match the other
// shared I/O-ish modules' baseline even though no logging is emitted yet. Gradle maps :shared:io ->
// shared/io/ automatically.
//
// NOTE: this sub-phase ONLY creates the module + its tests. It deliberately does NOT migrate
// register/despeckle onto it, and it adds NO `Files confined to shared.io` arch-rule —
// java.nio.file.Files is legitimately used broadly (shared.process temp files, shared.pdf scratch,
// app infrastructure), so confining it would be wrong.
dependencies {
    // The SLF4J facade only. The public signatures leak only JDK types (java.nio.file.Path, the
    // checked java.io.IOException), so `implementation` (not `api`) is enough — consumers compile
    // against the JDK alone.
    implementation(libs.slf4j.api)
}

// Coverage floor: the domain-ish/util floor the task fixed — 0.90 line / 0.85 branch. These are
// small, pure FS helpers, very testable against a temp directory, so a near-domain floor is honest;
// the only branches a temp-dir unit test cannot drive are the two `getFileName() == null` guards
// (the default filesystem never yields a null name for a walked regular file), which dilute the
// branch ratio slightly but stay above 0.85. The private no-instance constructors are exercised
// reflectively in the tests so the line floor is met honestly. This block is self-contained because
// the floor is not applied by any convention plugin.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
