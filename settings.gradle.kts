pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "my-pdf-tools-java"

// Each of the three apps keeps its hexagonal (ports & adapters) graph — one Gradle module per layer.
// Gradle maps the dotted project paths to directories automatically: :register:domain -> register/domain,
// :despeckle:port -> despeckle/port, and so on. The inter-module dependency edges declared in each
// module's build script enforce the layering at compile time; ArchUnit (in each :app) pins the
// intra-module rules the module graph cannot express.
//
// :register:runner is intentionally omitted: it is an orphan that was never built and depends on
// modules that do not exist.
//
// :pipeline is the unified single-pass pipeline (pipes-and-filters): it extracts a scan PDF once,
// runs despeckle -> register as filters over a shared image working-set, and composes the RTL
// spread as the only repack — no intermediate PDFs. A full hexagon like the three apps; its
// infrastructure wraps the existing apps' services as Source/Stage/Sink adapters, so the apps stay
// untouched. New processing stages plug in by adding one Stage adapter.
listOf("register", "despeckle", "tate-yoko-pdf", "pipeline").forEach { app ->
    listOf("domain", "port", "application", "infrastructure", "app").forEach { m ->
        include(":$app:$m")
    }
}

// Cross-app shared modules. The first is :shared:kernel — the framework-free primitives (the
// dpi<->px/mm Resolution and the exception-neutral Validators) that the three apps duplicate today.
// Gradle maps :shared:kernel -> shared/kernel/ automatically; the empty :shared parent project it
// implies needs no build script.
include(":shared:kernel")

// The second shared module: cross-app observability. It owns the single Severity -> slf4j Level
// translation (ExceptionMapper), the throwable->kind fallback, the PII path sanitizer, the fatal
// uncaught-exception handler, and the sysexits ExitCodes registry — the framework-touching layer the
// kernel deliberately excludes. Depends on :shared:kernel and slf4j-api. Gradle maps
// :shared:observability -> shared/observability/ automatically.
include(":shared:observability")

// The third shared module: the cross-app FFM imaging island. It unions the two formerly-separate
// Leptonica bindings (register's projection/geometry/deskew/scale set and despeckle's
// size-select/morphology/boolean/counting set) into one Foreign Function & Memory island (Leptonica)
// plus one owning RAII handle (Pix) exposing only PRIMITIVE pixel ops; the app-side deskew /
// keep-larger-than POLICY is layered on these primitives per app. No runtime deps. Applies
// p4suta.native-conventions so the FFM tests get --enable-native-access. Gradle maps
// :shared:imaging -> shared/imaging/ automatically.
include(":shared:imaging")

// The fourth shared module: :shared:arch-rules — a TEST-ONLY architecture module (no main sources).
// It owns a single ArchUnit suite (SharedArchitectureTest) that scans io.github.p4suta.shared
// directly and re-pins the FFM/MethodHandle confinement, the absence of package cycles, and the
// single JSpecify nullness vocabulary across the shared modules. This confinement USED to be
// enforced by register's/despeckle's per-app @AnalyzeClasses(io.github.p4suta.<app>) graphs, but
// those scan only their own app package — once the Leptonica binding moved into the shared imaging
// island the per-app rules went vacuous, leaving the shared imaging code UNCONFINED. This module
// closes that gap. Having no main sources, it needs no JaCoCo floor and is excluded from the root
// JaCoCo aggregation (see the root build.gradle.kts). Gradle maps :shared:arch-rules ->
// shared/arch-rules/ automatically.
include(":shared:arch-rules")

// The fifth shared module: :shared:cli — the cross-app CLI scaffolding, generalized from
// tate-yoko-pdf's best-of-breed Apache Commons CLI front end. This is the APP-LAYER scaffolding: the
// ONE shared module deliberately allowed to write to System.out/System.err and to depend on Apache
// Commons CLI. It owns the app-neutral glue every front end re-derives — the positional input
// resolver (with a parameterized directory filter), the stdin/stdout temp-file bridges, the
// continue-on-error BatchDriver, the throwable->exit-code CliExceptionHandler, and the
// int/double/enum/positional parse helpers — so register, despeckle, and tate-yoko-pdf adopt one
// copy instead of three. Depends on :shared:kernel + :shared:observability + commons-cli + slf4j.
// Gradle maps :shared:cli -> shared/cli/ automatically.
include(":shared:cli")

// The sixth shared module: :shared:process — the cross-app external-process plumbing, generalized
// from the best-of-breed bits the three apps each re-derived in their infrastructure.process /
// infrastructure.qpdf packages. It owns ToolPath (resolve an external tool to a Path: -D<property>
// override else PATH, returning Optional so the caller owns the "missing" policy; the property key
// stays a per-app PARAMETER so packaged app-image runs keep resolving), ProcessRunner (run under a
// timeout, returning a typed Result of exitCode + separate stdout/stderr + elapsed Duration, with a
// caller-supplied acceptable-exit-codes set that generalizes despeckle's qpdf-exit-3 tolerance), and
// Tasks (the parameterized parallel fan-out over a caller-owned pool). No domain exception is
// reachable, so it depends on slf4j-api only. Gradle maps :shared:process -> shared/process/
// automatically.
include(":shared:process")

// The seventh shared module: :shared:pdf — the cross-app PDF I/O island, generalized from
// despeckle's best-of-breed PDF adapters. It owns the four pieces the apps re-derive when they read,
// encode, and finish a PDF: PdfBoxJbig2Assembler (the despeckle SUPERSET that packs cleaned bitonal
// pages into a lossless-JBIG2 PDF via the jbig2 binary + PDFBox + opaque XMP copy), the
// PdfImagesCliExtractor (drives pdfimages/pdfinfo, parsing their text with the pure parser),
// PdfListingParser (the pure pdfinfo / pdfimages -list parser with the Python-parity dominant-DPI
// tie-break, lifted verbatim out of despeckle's :domain), and QpdfRunner (a neutral qpdf --linearize
// capability over :shared:process that RETURNS a Result and leaves the degrade-vs-throw policy to the
// app). The jbig2 encode keeps one documented local ProcessBuilder because `jbig2 -p` writes a RAW
// binary stream to stdout that the shared runner's UTF-8 decode would corrupt; everything else routes
// through :shared:process. Depends on :shared:process + pdfbox + xmpbox + slf4j. Gradle maps
// :shared:pdf -> shared/pdf/ automatically.
include(":shared:pdf")

// The eighth shared module: :shared:io — the cross-app filesystem-orchestration island,
// generalized from the byte-identical helpers register's RegistrationService and despeckle's
// DespeckleService each re-derived (the Phase-4 review flagged them O2/DUP2 — byte-identical across
// the two apps). It owns OutputDirs.prepare (ensure the output dir exists; reject a non-empty one
// without --force, preserving the EXACT operator message both apps' E2E + unit tests assert),
// CorpusFiles.collect (every regular file under a root whose NAME matches a caller-supplied glob,
// sorted by full path string so the deterministic processing order is preserved — the glob is a
// PARAMETER, never a hardcoded extension set), and CorpusFiles.mirrorDestination (mirror a source's
// input-relative path under an output root, swapping its extension to a caller-supplied @Nullable
// String so neither app's OutputFormat enum is imported). Pure java.nio.file.* otherwise, so it
// depends on slf4j-api only. Gradle maps :shared:io -> shared/io/ automatically.
include(":shared:io")
