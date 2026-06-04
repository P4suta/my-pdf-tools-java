plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The hexagonal ports: interfaces the application drives and the infrastructure adapters implement.
// They speak the domain vocabulary (records / enums / paths) and stay free of any adapter library —
// no Leptonica Pix, no PDFBox, no AWT crosses this boundary. Depends only on :domain.
dependencies {
    implementation(project(":register:domain"))
}
