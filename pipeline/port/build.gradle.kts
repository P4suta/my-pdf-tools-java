plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The hexagonal ports of the pipeline: Source (entry), Stage (filter), Sink (exit). They speak the
// domain vocabulary (Corpus) and paths only — no app or adapter library crosses this boundary, so
// the pipeline core stays decoupled from despeckle/register/tate, which are wired in as adapters in
// :infrastructure. Depends only on :pipeline:domain.
dependencies {
    implementation(project(":pipeline:domain"))
}
