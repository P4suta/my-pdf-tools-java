plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

dependencies {
    // Ports speak the domain vocabulary (records/enums) but stay free of any adapter library.
    implementation(project(":tate-yoko-pdf:domain"))
}
