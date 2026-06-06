# my-pdf-tools-java

A monorepo of command-line tools for self-scanned (自炊) Japanese-book PDFs and
their bitonal page images: three tools, the unified `pdfbook` pipeline that
chains them, and a Spring Boot web layer, on a shared hexagonal core.

## Tools

| App | What it does |
| --- | --- |
| [`register`](register/) | Aligns bitonal scans of 縦書き novel pages onto a fixed paper-size canvas, removing per-scan jitter (size / position / rotation). |
| [`despeckle`](despeckle/) | Removes scanner pepper-noise from bitonal scans while protecting ruby (振り仮名), 句読点, and dakuten/handakuten. |
| [`tate-yoko-pdf`](tate-yoko-pdf/) | Converts 縦書き scanned PDFs into right-to-left (RTL) two-page spreads that read correctly in any PDF viewer. |

## Unified pipeline

[`pdfbook`](pipeline/) runs the whole chain in a single pass: extract the scan's
pages **once**, then despeckle → register → RTL spreads, with **no intermediate
PDFs** (stages hand off image files in a temp work area; only the final spread is
repacked). A pipes-and-filters composition (a `Source`, ordered `Stage`s, a `Sink`)
over the three apps' services, so a new processing step is one `Stage`.

```sh
docker compose run --rm dev ./gradlew :pipeline:app:installDist
pipeline/app/build/install/pdfbook/bin/pdfbook scan.pdf -o book.pdf   # one book
pipeline/app/build/install/pdfbook/bin/pdfbook scans/ -o out/          # batch a directory
```

## Web app

[`webapp`](webapp/) is a Spring Boot web front end for `pdfbook`: upload a scan,
watch per-page progress over SSE, download the finished book. It runs pdfbook
out-of-process, so it has **zero** compile dependency on `pipeline`; a Svelte SPA
(`webapp/frontend/`) drives the API. See ADR
[0009](docs/adr/0009-spring-boot-web-layer-and-deployment.md).

## Layout

Each feature — the three tools, the `pipeline`, and the `webapp` — is a hexagonal
stack of five modules: `domain`, `port`, `application`, `infrastructure`, `app`
(composition root + runnable artifact). The pipeline's infrastructure wraps the
three tools' services as pipeline stages; `webapp` shells out to the packaged
pdfbook binary. Cross-cutting code lives in eight shared modules:

```
shared/
  kernel         core value types and utilities
  observability  logging, error mapping, exit codes
  imaging        bitonal image primitives
  cli            Apache Commons CLI front-end support
  process        external-process execution
  pdf            PDFBox-based PDF helpers
  io             filesystem / stream helpers
  progress       conversion progress / lifecycle events (SSE / JSONL)
```

`register` and `despeckle` reach Leptonica through the Java FFM API (Linux-only
here); `tate-yoko-pdf` has no native FFM dependency and packages cross-OS
(Linux / macOS / Windows) with a bundled qpdf.

## Build

The build is **Docker-only** — there is no host JVM. The dev image (root
`Dockerfile`) carries the toolchain: Liberica JDK 25 Full, Leptonica, the PDF
toolbox, fonts, and the linters.

```sh
docker compose run --rm dev ./gradlew build   # full quality gate, every module
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
