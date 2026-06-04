import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    // The JBIG2 assembler reads each cleaned page's pixel size / DPI through the shared Leptonica
    // Pix (the apps' original source for these — Leptonica reads raw PBM / G4-TIFF natively, ImageIO
    // does not), so the test JVM makes FFM downcalls into Leptonica and needs
    // `--enable-native-access` (+ `-Xshare:off` for the jacoco CDS warning). This is the same opt-in
    // despeckle's :infrastructure carried when the assembler lived there.
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    // Adds the `api` configuration so the one `:shared:process` type that surfaces in a PUBLIC
    // signature — QpdfRunner.linearize returns a ProcessRunner.Result — flows transitively to the
    // consuming app `infrastructure` packages. Without it a consumer could not name the returned
    // Result. PDFBox stays an implementation detail (no PDFBox type appears in any public
    // signature), so it does NOT need `api`.
    `java-library`
}

// The seventh cross-app shared module: the PDF I/O island, generalized from despeckle's
// best-of-breed PDF adapters. It owns the four pieces the apps re-derive when they read, encode, and
// finish a PDF:
//
//   * PdfBoxJbig2Assembler — packs a directory of cleaned bitonal pages into a lossless-JBIG2 PDF
//     (jbig2enc generic-region streams embedded verbatim as /JBIG2Decode image XObjects via PDFBox,
//     with opaque XMP/Info inheritance from a source PDF). Despeckle's SUPERSET signature is the
//     donor (per-page dpi, @Nullable source, OptionalInt forcedDpi, caller-owned pool + scratch).
//   * PdfImagesCliExtractor — extracts a PDF's embedded bitonal images as TIFFs by driving
//     pdfimages, splitting the page range across the pool; the textual pdfinfo/pdfimages -list
//     reports are parsed by the pure PdfListingParser.
//   * PdfListingParser — the pure (no PDFBox / no I/O) parser for pdfinfo / pdfimages -list text,
//     including the Python-parity dominant-DPI tie-break. Moved here verbatim from despeckle's
//     :domain so the extractor keeps using it.
//   * QpdfRunner — a neutral CAPABILITY over qpdf --linearize (+ optional --min-version /
//     --newline-before-endstream), with bundled-binary resolution via ToolPath. It RETURNS the
//     shared ProcessRunner.Result (and propagates IOException/TimeoutException/InterruptedException);
//     the failure POLICY (degrade vs throw) is deliberately NOT decided here — each app wraps it.
//
// The jbig2 encode is the one call site that does NOT route through ProcessRunner: `jbig2 -p` writes
// a RAW binary JBIG2 stream to stdout, and the shared runner decodes stdout to a UTF-8 String, which
// corrupts binary bytes. So the assembler keeps a single documented local ProcessBuilder that
// redirects stdout straight to the per-page scratch file. Everything else (tool resolution, the
// parallel fan-out, the text pdfinfo/pdfimages captures, the qpdf run with its exit-3 tolerance)
// goes through :shared:process. No domain exception is reachable here — a launch failure or
// unacceptable exit surfaces as a plain IOException — so this module depends on no app module.
dependencies {
    // The external-process plumbing: ToolPath resolves jbig2/pdfimages/pdfinfo/qpdf (the property
    // key stays a per-app PARAMETER so packaged app-image runs keep resolving their bundled
    // binaries); ProcessRunner runs the text captures and the qpdf pass; Tasks fans the per-page
    // encodes / page-range chunks out across the caller's pool. `api` because ProcessRunner.Result
    // surfaces in QpdfRunner.linearize's PUBLIC return type, so a consumer needs it on the compile
    // classpath.
    api(project(":shared:process"))
    // The shared Leptonica imaging island: the JBIG2 assembler reads each cleaned page's pixel size
    // and DPI through Pix (Leptonica reads the apps' raw PBM / Group-4 TIFF pages natively, where
    // ImageIO has no reader). `implementation` — Pix appears only inside the assembler, never in a
    // public signature.
    implementation(project(":shared:imaging"))
    // PDFBox builds the JBIG2 container (the /JBIG2Decode image XObjects, the page tree, the Info
    // dict copy). `implementation` — no PDFBox type appears in any public signature.
    implementation(libs.pdfbox)
    // xmpbox ships with PDFBox and shares its version — it carries the source XMP packet copied onto
    // the output's PDMetadata. `implementation` for the same reason.
    implementation(libs.xmpbox)
    // The SLF4J facade: the adapters log at debug/warn.
    implementation(libs.slf4j.api)
}

// Coverage floor: the same realistic infra-like 0.75 line / 0.60 branch the despeckle
// :infrastructure, :shared:imaging, and :shared:process modules use — this is exec / PDFBox
// plumbing, not branch-rich domain logic, so the domain-grade 0.95/0.90 the kernel/observability use
// would be dishonest. The pure PdfListingParser is exercised exhaustively by its own suite; the
// exec/PDFBox adapters' defensive branches (a missing tool, a tool that times out, a launch failure)
// cannot all be driven from a unit test even with the bundled binaries on PATH, so the lenient floor
// absorbs them. No class is excluded — the synthetic-input integration tests cover the genuine
// happy/error paths and the floor absorbs the few untestable catch arms. The same self-contained
// block the other shared modules carry, since the floor is not applied by any convention plugin.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
