# register

Align bitonal scans of Japanese vertical-writing (縦書き) novel pages onto a
fixed paper-size canvas. Every scan of the same book produces slightly different
page sizes, text-block positions and rotations; `register` removes that jitter
so every cleaned page sits identically on the page — ready to be bound back into
a uniform PDF.

It is a Java reimplementation of the (abandoned) Rust prototype
[`register-rs`](https://github.com/P4suta/register-rs), built on the same
Leptonica/FFM foundation as its sibling
[`despeckle`](https://github.com/P4suta/despeckle) so the two can later merge
into one self-scanning toolchain:

```text
pdfimages → despeckle (noise removal) → register (alignment) → re-pack to PDF
```

## What it does

For each page, in two passes over the directory:

1. **Detect the main text column.** A projection profile (per-row then
   per-column ink counts) finds the densest band; the running title (柱),
   footnote indices and other marginalia sit outside it and are excluded.
2. **Derive a reference layout.** Per parity (recto / verso), the median
   main-column box across the whole book becomes the target — robust to the odd
   badly scanned page.
3. **Register each page** onto a fresh paper-size canvas: straighten it
   (deskew), scale its column to the reference height, and place it onto the
   corpus type-area grid — the fixed rectangle the book is set in. By default
   (`--anchor top_right`) the page anchors, on each axis, whichever content edge
   sits on the grid: a body page by its top (the head margin, where every column
   starts) and right (the reading origin of vertical right-to-left text), an
   opener by its bottom (when the head is dropped) or left (when the rightmost
   columns are blank). The scan margin that overflows the canvas is cropped, so
   the text block — and the page number riding with it — lands at the same canvas
   position on every page of a parity. `--anchor center` instead
   balances each page's margins on the canvas without cropping, at the cost of
   that cross-page consistency. Pages whose column is far smaller than the
   reference (very sparse or blank pages) are passed through, centered.

Input and output are directories of bitonal **PBM / PNG / TIFF** images; PDFs
are never touched (that is the re-packing step's job).

## Usage

```bash
just run scans/book out/book                 # defaults: shiroku paper, 400 dpi
just run scans/book out/book --paper b6 --dpi 600 --force
just run scans/book out/book --no-scale --no-deskew --format png
```

| option | default | meaning |
| --- | --- | --- |
| `--paper` | `auto` | `auto`, a standard name (`shiroku/a4/a5/a6/b5/b6/shinsho`), or a custom `WxH` mm (e.g. `127x188`). `auto` snaps the median scanned page to the nearest standard book size — robust to a book scanned a little under nominal (binding trim / paper shrink) *and* over nominal (loose crop / scan margin, which it treats as croppable and so does not let pull the choice up a size) — falling back to the exact measured size when no standard is close |
| `--dpi` | inherit scan | output resolution; fixes the canvas pixel size. Default: inherit the input scan's own resolution (falls back to `400` for untagged inputs such as raw PBM) |
| `--format` | `same` | `same/pbm/png/tiff` |
| `--glob` | `*.{pbm,png,tiff,tif}` | input file-name glob |
| `-j`/`--jobs` | CPUs | worker threads |
| `--no-deskew` | off | turn off straightening each page before detection (on by default) |
| `--no-scale` | off | turn off scaling each column to the reference height (on by default) |
| `--outlier-ratio` | `0.5` | a column smaller than this fraction of the reference is centered, not registered |
| `--anchor` | `top_right` | `top_right` (pin column top-right to the reference, cropping margin overflow — same text-block position on every page) or `center` (balance each page's margins, no cropping) |
| `--force` | off | overwrite a non-empty output directory |
| `--diag` | off | write diagnostic artifacts to this directory (see below) |
| `--flipbook` | off | with `--diag`, also assemble an animated WebP flip-book of the registered pages (needs libwebp's `img2webp`) |

Enum values (`--format`, `--anchor`) are case-insensitive.

### Diagnostics (`--diag <dir>`)

Registration is geometric and easy to "eyeball wrong", so `--diag` writes a set
of artifacts that show *what the alignment actually did* — without touching the
bilevel output:

- **`NNNN-<page>.diag.webp`** — one per page: the page with its detected column
  (green), the band it came from (blue), the reference box (orange), the
  projection profiles and the numeric placement.
- **`corpus-overlay.webp`** — the whole corpus at a glance. For each parity it
  overlays every registered page's detected column **before** (raw page space —
  the scan-jitter cloud) and **after** (canvas space — where it landed). A tight
  *after* stack around the orange median grid box, where the *before* cloud was
  wide, is the visible proof the jitter collapsed onto the grid.
- **`residuals.webp`** — per-page distance from each placed column edge to the
  grid edge, plotted by page index (recto ●, verso ▲), one panel per axis. Low
  and flat means tight registration; a spike names the page that drifted. (Same
  numbers as `summary.txt`.)
- **`pages.jsonl`** / **`summary.txt`** — the machine-readable per-page log and
  the aggregate run summary.
- **`flipbook.webp`** (with `--flipbook`) — the registered pages as an animated
  WebP; flip through it and the text block stays steady while the margins move.
  Built via libwebp's `img2webp` (from the `PATH` or `-Dregister.img2webp.path`);
  if the tool is missing the flip-book is skipped and the rest is unaffected.

## Architecture

Five Gradle modules under `io.github.p4suta.register`, a **hexagonal (ports & adapters) graph** in
which a layer violation does not compile — the inter-module dependency edges put the offending class
off the classpath. ArchUnit (in `:app`) pins the intra-module rules the module graph cannot express
(the FFM island, PDFBox / `ProcessBuilder` / `System.out` / filesystem confinement, the single
JSpecify nullness vocabulary). Shared build conventions live in `build-logic/` as the
`register.{java,test,quality}-conventions` plugins.

```
:app ──▶ :application ──▶ :port ──▶ :domain   (pure; no project or third-party runtime deps)
  └────▶ :infrastructure ──▶ :port, :domain   (PDFBox / Leptonica(FFM) / external binaries only here)
```

The `Throwable` → exit-code mapping and the fatal uncaught-exception handler the CLI installs now
come from the cross-app `:shared:observability` + `:shared:cli` modules (they used to be a per-app
`:observability` module).

| module | role |
| --- | --- |
| `:domain` | the pure kernel — no project dependency, no third-party runtime: the value types (`Box`, `Transform`, `Canvas`, `PaperSize`, `Anchor`, `Parity`, `OutputFormat`, `RegisterOptions`, the diagnostic records) and the framework-free algorithms (`ProjectionProfile`, the per-parity `Reference`, `TransformPlanner`) |
| `:port` | the interfaces the application drives and the infrastructure implements — `PageRegistrar`, `PdfImageExtractor`, `Jbig2Assembler`, `Reporter`/`ReporterFactory` — speaking only domain types and file paths (no `Pix`, no PDFBox crosses the boundary) |
| `:application` | orchestration: the corpus `RegistrationService` (directory walk, fixed thread pool, the two-pass analyze → reference → render) and the `register pipeline` drivers (`PdfPipelineService`, `PdfBatchService`). Sees only `:domain` + `:port` |
| `:infrastructure` | the adapters: `Leptonica` (the one FFM binding island) + `Pix` (RAII handle) + the pixel-pushing `LeptonicaPageRegistrar` / `MainColumnDetector`; the PDFBox + `pdfimages`/`jbig2` PDF wrappers; the opt-in `--diag` renderers; the native-tool/process helpers. The one module that uses PDFBox and crosses the FFM/process boundary; publishes the `TestImages` fixture |
| `:app` | the composition root and runnable artifact: `Main`, the Apache Commons CLI front end (`RegisterCommand`/`PipelineCommand`, shared parsing in `CliSupport`), and the self-contained jpackage app-image distribution (jlink + shadow + staged natives) |

The project-wide JaCoCo coverage aggregation (`just coverage`) lives in the root build, not a module.

`:domain` performs no directory or thread work and never touches Leptonica, so a future GUI could
reuse it unchanged. The detection / planning logic (`ProjectionProfile`, `Reference`,
`TransformPlanner`, `PaperSize`) is pure Java; the Leptonica adapter does the pixel work
(`pixCountPixelsByRow/Column`, `pixDeskew`, `pixScaleToSize`, `pixRasterop`) behind the
`PageRegistrar` port.

## Requirements

- **Leptonica** (`liblept.so`) at run time — the dev image installs
  `libleptonica-dev`. Override the resolved path with
  `-Dregister.leptonica.path=/path/to/liblept.so` if needed.
- **JDK 25** (FFM is final since JDK 22; the build pins the 25 toolchain).
- Run with `--enable-native-access=ALL-UNNAMED` — `:app`'s `run`/`test` and the
  bundled launcher already pass it (it is set in the conventions and `gradle.properties`).

## Quality gates

`./gradlew build` (or `just build`) runs the full gate, mirrored by CI:

- **Spotless** + google-java-format (AOSP, 100 columns)
- **Error Prone** on every compile, `-Werror`
- **NullAway** (JSpecify, `@NullMarked`) at ERROR on both main and test sources
- **SpotBugs** at max effort
- **Javadoc** doclint (`-Xdoclint:all,-missing`, `-Werror`) — a broken `{@link}` fails the build
- **ArchUnit** — the hexagonal layering, FFM / `MethodHandle` confinement, PDFBox / `ProcessBuilder`
  / filesystem / standard-stream limits, Commons CLI pinned to the shell, no package cycles,
  JSpecify-only nullness
- **JaCoCo** coverage floors on the unit-testable modules (`jacocoTestCoverageVerification`);
  `:application` and `:app`, which are exercised cross-module, are tracked via the aggregated
  coverage report (`just coverage`) instead
- **JUnit** — projection-profile and planner unit tests, an FFM smoke test, and
  a directory end-to-end run, plus the ArchUnit architecture suite

Out of the `build` gate, run on demand:

- `just coverage` — project-wide aggregated coverage: the root `jacoco-report-aggregation` merges
  every module's JaCoCo data into one report, so cross-module end-to-end coverage is credited (e.g.
  `:application`, which is unit-light on its own, is covered by `:app`'s E2E suites)
- `just mutation` — pitest mutation testing for the pure-logic `:domain`
- `just rewrite` — an advisory **OpenRewrite** pass (never blocking)

## License

Dual-licensed under either of

- MIT license ([LICENSE-MIT](LICENSE-MIT))
- Apache License 2.0 ([LICENSE-APACHE](LICENSE-APACHE))

at your option.
