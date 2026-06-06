# pdfbook web — SPA

A small **Svelte 5 + TypeScript + Vite** single-page app for the pdfbook web API: pick a scan PDF,
choose options, watch progress over Server-Sent Events, download the composed book. Managed with
**pnpm**; formatted and linted with **Biome** (the repo-root `biome.json` covers this `.ts`/`.svelte`
code — run `just fmt` / `just fmt-check`, or `pnpm run check` here).

It is deliberately **not** wired into the Gradle build (`./gradlew check` stays Java-only and fast).
Build it with pnpm and either run the dev server or hand the static bundle to the Spring Boot server.

## Develop

```bash
cd webapp/frontend
pnpm install
pnpm dev               # Vite dev server on http://127.0.0.1:5173
```

In another terminal, start the API server (it listens on :8080; the dev server proxies `/api` to it):

```bash
# build pdfbook once and put its launcher on PATH (or set -Dp4suta.pdfbook.binary / app.pdfbook-binary)
./gradlew :pipeline:app:installDist
PATH="$(pwd)/pipeline/app/build/install/pdfbook/bin:$PATH" ./gradlew :webapp:app:bootRun
```

Open http://127.0.0.1:5173.

## Check / build

```bash
pnpm run check         # svelte-check (types) — Biome lint/format is run repo-wide via `just`
pnpm run build         # type-check + emit dist/
```

## Production bundle

Serve `dist/` however you like. The simplest option is to copy it into the server's static resources
so the bootJar serves the UI itself:

```bash
cp -r dist/* ../app/src/main/resources/static/
```

(Then the SPA is available at the server's own origin, e.g. http://127.0.0.1:8080.)
