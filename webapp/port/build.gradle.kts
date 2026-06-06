plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The web feature's driven ports: the interfaces the application calls and the infrastructure
// adapters implement — JobStore (persistence), ConversionEngine (run pdfbook), ConversionExecutor
// (bounded async), Workspace (per-job files), ProgressPublisher (fan-out to SSE), JobIdGenerator —
// plus the QueueFullException the executor raises at capacity. They speak the domain vocabulary and
// the shared progress events only; no Spring, no adapter library crosses this boundary. Depends on
// :webapp:domain and :shared:progress. Gradle maps :webapp:port -> webapp/port/ automatically.
dependencies {
    implementation(project(":webapp:domain"))
    implementation(project(":shared:progress"))
}
