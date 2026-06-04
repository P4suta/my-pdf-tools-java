plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// Port interfaces only: domain / JDK types in their signatures, nothing third-party.
dependencies {
    implementation(project(":despeckle:domain"))
}
