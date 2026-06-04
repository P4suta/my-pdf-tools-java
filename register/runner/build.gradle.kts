plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// Corpus orchestration: the one place that walks the directory tree and drives the worker pool, in
// two passes (analyze every page to a per-parity median Reference, then place each page against it).
// Depends on core, diag (the diagnostics observer) and the util leaf.
dependencies {
    implementation(project(":core"))
    implementation(project(":diag"))
    implementation(project(":util"))
}

// No per-module coverage floor here: Runner is the corpus orchestration whose two-pass directory walk
// and worker pool are exercised end-to-end by the :cli and :pipeline suites (cross-module), so its own
// unit test understates real coverage. That cross-module coverage is counted in the aggregated
// :coverage report (`just coverage`).
