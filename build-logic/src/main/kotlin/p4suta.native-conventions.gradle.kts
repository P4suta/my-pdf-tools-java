// The ONE place native access is granted across the monorepo. A module opts in by applying this
// plugin (e.g. the FFM/Leptonica infrastructure and the apps that run or test against native
// libraries); java-conventions deliberately does NOT grant native access globally anymore, so
// pure modules (:domain, :port) never see the flag.
//
// What this plugin does:
//   * publishes the canonical `nativeAccessArgs` list via `extra[...]` so an app's distribution /
//     run script and any custom JavaExec can reuse the exact same flag list (despeckle's approach);
//   * applies it to every JavaExec (covers the `application` plugin's `run` task, which is a
//     JavaExec) and to every Test JVM;
//   * adds `-Xshare:off` to Test JVMs to silence the CDS warning that fires when jacoco's javaagent
//     appends to the bootstrap classpath under native access.
plugins {
    java
}

// FFM native access for the JVM running our code (Leptonica/qpdf bindings, etc.). Published on the
// project's `extra` so :app's distribution start-script args and the test conventions reuse the
// exact same list rather than duplicating the literal.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")
extra["nativeAccessArgs"] = nativeAccessArgs

// `application`'s `run` task is a JavaExec, so this also covers application runs.
tasks.withType<JavaExec>().configureEach {
    jvmArgs(nativeAccessArgs)
}

tasks.withType<Test>().configureEach {
    // Native access for the test JVM; -Xshare:off silences the CDS warning jacoco's javaagent
    // triggers when it appends to the bootstrap classpath.
    jvmArgs(nativeAccessArgs)
    jvmArgs("-Xshare:off")
}
