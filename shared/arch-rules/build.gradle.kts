plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
}

// A TEST-ONLY architecture module. It owns no main sources — only a single ArchUnit test suite
// (SharedArchitectureTest) that imports the COMPILED production classes of the cross-app shared
// modules and pins the boundaries the per-app @AnalyzeClasses(io.github.p4suta.<app>) graphs can no
// longer see.
//
// Why this module exists: each app's ArchitectureTest scans only io.github.p4suta.<app>, so the
// FFM/MethodHandle confinement that USED to be enforced on register's/despeckle's leptonica package
// is now UNENFORCED on the shared imaging code — those per-app rules pass vacuously because the
// shared package is outside the imported graph. This module re-establishes that confinement by
// scanning io.github.p4suta.shared directly.
//
// It applies ONLY java- + test-conventions:
//   * NOT quality-conventions: there are no main sources to Spotless/SpotBugs-gate, and the test
//     sources ride the same java-conventions formatting/-Werror gate already.
//   * NOT native-conventions: ArchUnit is static bytecode analysis — it never loads liblept, so no
//     --enable-native-access is needed (or wanted) here.
//
// No JaCoCo coverage floor is declared (unlike :kernel/:observability/:imaging): with NO main
// sources there is no production bytecode to cover, so a floor would be meaningless. This module is
// likewise EXCLUDED from the root JaCoCo aggregation in the root build.gradle.kts for the same
// reason — see the comment on the aggregatedModules list there.

dependencies {
    // The production shared modules whose compiled classes this suite imports and confines. These
    // are test-scope edges: the analyzed graph is io.github.p4suta.shared, which lives in these
    // jars. No main source here depends on them (there is none). :shared:cli is on this list so its
    // classes are in the imported graph: the Commons-CLI and standard-stream confinement rules pin
    // org.apache.commons.cli.. and System.out/err to io.github.p4suta.shared.cli.. — which means
    // shared.cli must BE in the graph for those rules to verify the boundary rather than pass
    // vacuously (the exact failure mode this module exists to prevent).
    testImplementation(project(":shared:kernel"))
    testImplementation(project(":shared:observability"))
    testImplementation(project(":shared:imaging"))
    testImplementation(project(":shared:cli"))
    // :shared:process is on this list for the same reason as :shared:cli above: the
    // ProcessBuilder/Process confinement rule pins java.lang.ProcessBuilder and java.lang.Process to
    // io.github.p4suta.shared.process.. (ProcessRunner is the lone external-process launcher) — which
    // means shared.process must BE in the imported graph for that rule to verify the boundary rather
    // than pass vacuously (the exact failure mode this module exists to prevent).
    testImplementation(project(":shared:process"))
    // :shared:pdf is on this list for the same reason as :shared:cli/:shared:process above: the
    // PDFBox confinement rule pins org.apache.pdfbox.. and org.apache.xmpbox.. to
    // io.github.p4suta.shared.pdf.. (PdfBoxJbig2Assembler is the lone PDF assembler) — which means
    // shared.pdf must BE in the imported graph for that rule to verify the boundary rather than pass
    // vacuously (the exact failure mode this module exists to prevent).
    testImplementation(project(":shared:pdf"))
    // ArchUnit's JUnit 5 engine. p4suta.test-conventions already puts archunit-junit5 on the test
    // classpath; this explicit edge makes the dependency this module's reason for existing visible
    // at the call site.
    testImplementation(libs.archunit.junit5)
}
