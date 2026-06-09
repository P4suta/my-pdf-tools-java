# webapp

A Spring Boot web front end for [`pdfbook`](../pipeline/): upload a scanned PDF,
watch per-page progress over SSE, download the finished book. The server shells
out to the packaged `pdfbook` binary (one subprocess per job) and streams its
JSONL progress to the browser; a Svelte single-page app (`frontend/`) drives the
API.

## API

All endpoints are under `/api/v1`:

| method | path | purpose |
| --- | --- | --- |
| `POST` | `/jobs` | submit a conversion (multipart: the PDF + options); returns a job id |
| `POST` | `/jobs/probe` | check the result cache for an identical job without uploading |
| `GET` | `/jobs/{id}` | job status (`state`, and on failure `errorKind` + `errorMessage`) |
| `GET` | `/jobs/{id}/events` | progress event stream (SSE) until a terminal event |
| `GET` | `/jobs/{id}/result[/{filename}]` | download the finished PDF |

Identical jobs are de-duplicated and served from a result cache, so a re-submit
skips the upload and completes instantly.

## Errors

The backend and the shared kernel are presentation-free: a failure is reported as
a stable `errorKind` token (the same error vocabulary the CLI uses, e.g.
`OUTPUT_CONFLICT`, `IMAGE_UNREADABLE`) plus a developer-facing detail. The web UI
owns its Japanese text: `frontend/src/App.svelte` maps each kind to a Japanese
message (`ERROR_KIND_JA`), falling back to the detail then a generic line. The CLI
maps the same kinds to English in `shared/cli` — neither surface's language leaks
into the other.

## Build & run

Built and run only inside the dev container (no host JDK / Node):

```sh
just web-serve   # build the SPA into the bootJar, run the server on :8080 (bundles pdfbook)
just web-ui      # Vite dev server with HMR, proxying /api to :8080 (pair with web-serve)
just web-stop    # stop the web-serve container
just web-image   # build the self-contained runtime Docker image (SPA + pdfbook + native tools)
just web-package # build the Docker-free, JDK-free app-image (nests the pdfbook app-image)
just web-app-run # build + run that app-image's server (Linux; PATH-empty by design)
```

`WEB_PORT=9090 just web-serve` overrides the published host port. The runtime
image runs with `docker run --rm -p 127.0.0.1:8080:8080 pdfbook-web`.

The server ships two complementary ways — the runtime Docker image (the default
for a resident server) and the self-contained app-image — built cross-OS like the
CLI tools. See [Distribution & packaging](../docs/distribution.md) for the build
models, per-OS prerequisites, and the runtime property scheme.

Double-clicking the app-image launcher (`pdfbook-web` / `pdfbook-web.exe`) starts
the server and, like a dev server, **prints a clickable `http://localhost:<port>`
link in the console and opens it in your default browser** automatically. The
console window it opens is the running server — close it to stop. (Auto-open is
skipped when headless or with `--app.open-browser=false`, and in the `prod`
profile / Docker image; the printed link still works in every case.)

The frontend toolchain (pnpm, Vite, Svelte, Biome/Prettier) lives in `frontend/`
and is exercised through the same dev container; CI runs `just fmt-check` and the
frontend's strict lint.
