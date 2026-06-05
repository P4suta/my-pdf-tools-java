# my-pdf-tools-java

A monorepo of three command-line tools for self-scanned (自炊) Japanese-book PDFs
and the bitonal page images inside them, built on a shared hexagonal core.

## Tools

| App | What it does |
| --- | --- |
| [`register`](register/) | Aligns bitonal scans of 縦書き novel pages onto a fixed paper-size canvas, removing per-scan jitter (size / position / rotation). |
| [`despeckle`](despeckle/) | Removes scanner pepper-noise from bitonal scans while protecting ruby (振り仮名), 句読点, and dakuten/handakuten. |
| [`tate-yoko-pdf`](tate-yoko-pdf/) | Converts 縦書き scanned PDFs into right-to-left (RTL) two-page spreads that read correctly in any PDF viewer. |

## Unified pipeline

[`pdfbook`](pipeline/) runs the whole chain in a single pass — extract the scan's
pages **once**, then despeckle → register → RTL spreads — with **no intermediate
PDFs** (the stages hand off image files in a temp work area; only the final spread
is repacked). It is a pipes-and-filters composition (a `Source`, ordered `Stage`s,
a `Sink`) over the three apps' services, so a new processing step is one `Stage`.

```sh
docker compose run --rm dev ./gradlew :pipeline:app:installDist
pipeline/app/build/install/pdfbook/bin/pdfbook scan.pdf -o book.pdf   # one book
pipeline/app/build/install/pdfbook/bin/pdfbook scans/ -o out/          # batch a directory
```

## Layout

Each app — and the unified `pipeline` — is a hexagonal stack of five modules —
`domain`, `port`, `application`, `infrastructure`, `app` (composition root +
runnable artifact); the pipeline's infrastructure wraps the three apps' services as
pipeline stages. Cross-cutting code lives in seven shared modules:

```
shared/
  kernel         core value types and utilities
  observability  logging, error mapping, exit codes
  imaging        bitonal image primitives
  cli            Apache Commons CLI front-end support
  process        external-process execution
  pdf            PDFBox-based PDF helpers
  io             filesystem / stream helpers
```

`register` and `despeckle` reach Leptonica through the Java FFM API (Linux-only
here); `tate-yoko-pdf` has no native FFM dependency and packages cross-OS
(Linux / macOS / Windows) with a bundled qpdf.

## Build

The build is **Docker-only** — there is no host JVM. The self-contained dev
image (root `Dockerfile`) carries the entire toolchain (Liberica JDK 25 Full,
Leptonica, the PDF toolbox, fonts, and the linters).

```sh
docker compose run --rm dev ./gradlew build   # full quality gate, all 27 modules
docker compose run --rm dev just build        # same, via the justfile
docker compose run --rm dev just lint          # peripheral linters
```

The quality gate runs Error Prone + NullAway (`-Werror`), SpotBugs (max effort),
Spotless, the JUnit/jqwik/ArchUnit suites, and per-module JaCoCo coverage
verification.

## CI & artifacts

- **Actions** — `ci` (build + check, lint), `distribution` (per-OS jpackage /
  installDist + smoke), `docs`, `dev-image`, `freshness`.
- **Javadoc (Pages)** — https://p4suta.github.io/my-pdf-tools-java/
- **dev image (GHCR)** — `ghcr.io/p4suta/my-pdf-tools-java-dev:latest`

## License

Each app keeps its own license (see its directory): `register` and `despeckle`
are dual-licensed MIT OR Apache-2.0; `tate-yoko-pdf` is MIT.
