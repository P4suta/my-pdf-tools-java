# my-pdf-tools-java error model — authoritative spec

Status: SOURCE OF TRUTH. Test rewrites and code changes MUST match this document.
Do **not** reverse-engineer exit codes from current code; the current `register`/`despeckle`
`0/1/2` scheme is being deliberately uplifted to the sysexits scheme below (user-approved).

Scope: the three CLIs in this monorepo — `tate-yoko-pdf`, `register`, `despeckle`.
`tate-yoko-pdf` is the best-of-breed reference; its model is generalized into shared
primitives (`:shared:kernel`, `:shared:observability`) that `register` and `despeckle`
then adopt.

---

## 1. The contract: `ErrorCategory` + `Severity`

### 1.1 `Severity` (kernel enum)

A **kernel** enum, NOT slf4j `Level`, so `:shared:kernel` stays dependency-free (only the
conventions' `compileOnly` jspecify annotations).

```java
package io.github.p4suta.shared.kernel.error;

public enum Severity {
    INFO,
    WARN,
    ERROR
}
```

`:shared:observability` owns the single mapping to slf4j:

| Severity | org.slf4j.event.Level |
|----------|-----------------------|
| INFO     | INFO                  |
| WARN     | WARN                  |
| ERROR    | ERROR                 |

The mapping lives ONLY in the observability layer; kernel never imports slf4j.

### 1.2 `ErrorCategory` (kernel interface)

```java
package io.github.p4suta.shared.kernel.error;

public interface ErrorCategory {
    String defaultUserMessage();   // Japanese, user-facing
    boolean isClientFault();       // true = caller's input/usage; false = internal/environmental
    int exitCode();                // sysexits-flavored process exit code
    Severity severity();           // INFO | WARN | ERROR
    String name();                 // stable identifier (enum name); appears in `Error[NAME]: ...`
}
```

`CommonErrorKind` and each app's `ErrorKind` are **enums that implement `ErrorCategory`**.
This replaces tate's `EnumMap<ErrorKind, Template>(exitCode, logLevel)` table — exit code and
severity now live ON the enum constant, not in a side table in the mapper. The mapper keeps only
the `Severity -> Level` translation and the throwable→kind fallback (section 5).

### 1.3 Invariant: severity tracks clientFault

Across all reference data points (tate's 8 kinds + the 3 `CommonErrorKind`), the rule holds and
MUST continue to hold for every new kind:

- `isClientFault() == true`  ⟹ `severity() == WARN`  (bad input/usage; logged at WARN, not alarming)
- `isClientFault() == false` ⟹ `severity() == ERROR` (internal/environmental; logged at ERROR)

(`INFO` is reserved; no current kind uses it.) A `clientFault=true` constant with `severity=ERROR`
is a spec violation — do not introduce one.

---

## 2. `CommonErrorKind` (shared, in `:shared:kernel`)

The generic kinds every app reuses. Messages are Japanese, matching tate's tone.

| name              | exitCode | severity | clientFault | defaultUserMessage |
|-------------------|----------|----------|-------------|--------------------|
| INVALID_PARAMETER | 64       | WARN     | true        | `入力値が不正です。` |
| OUT_OF_MEMORY     | 137      | ERROR    | false       | `メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。` |
| INTERNAL          | 70       | ERROR    | false       | `予期しないエラーが発生しました。` |

- `64` = `EX_USAGE`-family data error (bad CLI value / precondition).
- `137` = `128 + SIGKILL`, conventional OOM exit.
- `70` = `EX_SOFTWARE`, internal/unexpected.

These three coincide **exactly** (code + severity + fault + message) with three of tate's existing
8 kinds (`INVALID_PARAMETER`, `OUT_OF_MEMORY`, `INTERNAL`). That coincidence is intentional and
validates the chosen `CommonErrorKind` values.

---

## 3. Per-app `ErrorKind` tables

Reuse `CommonErrorKind` wherever a failure is just invalid-param / OOM / internal. Add an
app-specific kind ONLY where it carries a genuinely distinct exit code or user message.

The **trigger** column is prescriptive: it names the current throw site and the kind it must be
**retyped** to. "Retype" means the site is changed to throw the app's domain exception carrying
that `ErrorCategory` (section 4), instead of relying on the generic fallback — because the generic
fallback alone would route several of these to the wrong code (section 5 explains which).

### 3.1 `tate-yoko-pdf` — keep all 8 (re-expressed as `ErrorCategory`)

No code/exit-code change. The only refactor: `ErrorKind` implements `ErrorCategory`
(adds `exitCode()` + `severity()` from the table that used to live in `ExceptionMapper`'s `EnumMap`).
Domain exception stays `SpreadException`. Messages unchanged.

| name                    | exitCode | severity | clientFault | userMessage | trigger |
|-------------------------|----------|----------|-------------|-------------|---------|
| PDF_CORRUPTED           | 65       | WARN     | true        | `PDFを読み込めませんでした。ファイルが破損している可能性があります。` | PDFBox load/parse failure |
| PDF_PASSWORD_PROTECTED  | 77       | WARN     | true        | `PDFがパスワードで保護されているため処理できません。` | encrypted PDF |
| PDF_NOT_FOUND           | 66       | WARN     | true        | `指定された PDF ファイルが見つかりません。` | input PDF missing |
| PDF_INVALID_PAGE        | 65       | WARN     | true        | `PDFのページ指定が不正です。` | bad page range |
| PDF_WRITE_FAILED        | 73       | ERROR    | false       | `出力 PDF の書き出しに失敗しました。` | output PDF write failure |
| INVALID_PARAMETER       | 64       | WARN     | true        | `入力値が不正です。` | bad CLI value / precondition |
| OUT_OF_MEMORY           | 137      | ERROR    | false       | `メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。` | `OutOfMemoryError` |
| INTERNAL                | 70       | ERROR    | false       | `予期しないエラーが発生しました。` | anything else |

Note: tate's `PDF_WRITE_FAILED` is `clientFault=false` (output write is environmental, not the
caller's input), so `severity=ERROR` — consistent with the section 1.3 invariant.

sysexits codes tate uses, for reference: `0` OK, `1` generic, `2` usage,
`64` EX_USAGE, `65` EX_DATAERR, `66` EX_NOINPUT, `70` EX_SOFTWARE, `73` EX_CANTCREAT,
`77` EX_NOPERM, `137` OOM. (`74` EX_IOERR / `78` EX_CONFIG exist in `CliExitCodes` but no
current kind maps to them — leave them defined, unused.)

### 3.2 `register` — new `ErrorKind` (was flat `0/1/2`)

Reuses `CommonErrorKind` for generic cases; adds these app kinds:

| name              | exitCode | severity | clientFault | userMessage | trigger (current throw → retype to) |
|-------------------|----------|----------|-------------|-------------|-------------------------------------|
| INPUT_NOT_FOUND   | 66       | WARN     | true        | `入力ファイルまたはディレクトリが見つかりません。` | `IOException("input PDF not found: ...")` (PdfPipelineService) → INPUT_NOT_FOUND |
| IMAGE_UNREADABLE  | 65       | WARN     | true        | `画像を読み込めませんでした。対応していない形式か、ファイルが破損している可能性があります。` | `IllegalArgumentException("Leptonica could not read image: ...")` (Pix) and `IOException("ImageIO could not read back ...")` (Diagnostics) → IMAGE_UNREADABLE |
| OUTPUT_CONFLICT   | 73       | WARN     | true        | `出力先がすでに存在します。--force で上書きできます。` | `IOException("... already exists; pass --force to overwrite")` (PdfPipelineService) → OUTPUT_CONFLICT |
| NATIVE_TOOL_FAILED| 70       | ERROR    | false       | `外部ツールの実行に失敗しました。pdfimages / pdfinfo / jbig2 がインストールされているか確認してください。` | `IOException("... not found on PATH ...")`, `IOException("... failed with exit code N")`, `IOException("... timed out ...")`, `IOException("no PNG writer available for ...")` (NativeTools / RegistrationService) → NATIVE_TOOL_FAILED |

Reuse of `CommonErrorKind` in register:
- `INVALID_PARAMETER` (64) — value validation: `--dpi`/`--outlier-ratio`/`--paper` (`PaperSize`,
  `RegisterOptions`), `pdfinfo` Pages parse, "registered page without a column/reference",
  "cannot derive a reference from zero observations". (All currently `IllegalArgumentException`;
  these stay in the 64 bucket — see section 5.)
- `OUT_OF_MEMORY` (137), `INTERNAL` (70) — as usual.

Not-found is split OUT of IMAGE_UNREADABLE into INPUT_NOT_FOUND (66) to mirror tate's
NOT_FOUND/CORRUPTED distinction; image **read** failures (unreadable/corrupt/unsupported) are 65.

### 3.3 `despeckle` — new `ErrorKind` (was flat `0/1/2`)

Structurally identical to register; the native tool set is `pdfimages` / `pdfinfo` / `jbig2` /
`qpdf` (note `qpdf`'s "succeeded with warnings" exit 3 is treated as success, not a failure —
unchanged).

| name              | exitCode | severity | clientFault | userMessage | trigger (current throw → retype to) |
|-------------------|----------|----------|-------------|-------------|-------------------------------------|
| INPUT_NOT_FOUND   | 66       | WARN     | true        | `入力ファイルまたはディレクトリが見つかりません。` | `IOException("input PDF not found: ...")`, `IOException("input image directory not found: ...")` (PdfPipelineService / Jbig2PackService) → INPUT_NOT_FOUND |
| IMAGE_UNREADABLE  | 65       | WARN     | true        | `画像を読み込めませんでした。対応していない形式か、ファイルが破損している可能性があります。` | `IllegalArgumentException("Leptonica could not read image: ...")` (Pix) → IMAGE_UNREADABLE |
| OUTPUT_CONFLICT   | 73       | WARN     | true        | `出力先がすでに存在します。--force で上書きできます。` | `IOException("... already exists; pass --force to overwrite")` (PdfPipelineService) → OUTPUT_CONFLICT |
| NATIVE_TOOL_FAILED| 70       | ERROR    | false       | `外部ツールの実行に失敗しました。pdfimages / pdfinfo / jbig2 / qpdf がインストールされているか確認してください。` | `IOException("... not found on PATH ...")`, `IOException("... failed with exit code N")`, `IOException("... timed out ...")`, `IOException("no PNG writer available for ...")` (NativeTools) → NATIVE_TOOL_FAILED |

Reuse of `CommonErrorKind` in despeckle:
- `INVALID_PARAMETER` (64) — `--dpi`/`--speck-size`/`--isolated-dust-size` validation
  (`ProcessOptions`), `pdfinfo` Pages parse. (Currently `IllegalArgumentException` → stays 64.)
- `OUT_OF_MEMORY` (137), `INTERNAL` (70) — as usual.

**No distinct NOT_BITONAL kind.** Confirmed by grep: despeckle has no depth/bpp/bilevel guard.
A non-bitonal **or** otherwise unreadable image surfaces from the same single site
(`Pix.java:39`, `IllegalArgumentException("Leptonica could not read image")`) and is mapped to
`IMAGE_UNREADABLE` (65). If Leptonica accepts the image but a later step rejects its depth, that
currently propagates as an unexpected `IllegalStateException` → `INTERNAL` (70). Do not invent a
NOT_BITONAL kind — there is no real error site for it.

---

## 4. Mechanism: app domain exception carrying an `ErrorCategory`

`register` and `despeckle` each gain a domain exception mirroring tate's `SpreadException`:
`RegisterException` / `DespeckleException`, unchecked, holding
`(ErrorCategory kind, String userMessage, @Nullable String technicalDetail, @Nullable Throwable cause)`
with `of(kind)` / `of(kind, cause)` / `withDetail(kind, detail, cause)` factories.

This is REQUIRED, not optional: the throwable→kind fallback (section 5) cannot, by class alone,
tell `IllegalArgumentException("Leptonica could not read image")` (→ 65) from
`IllegalArgumentException("dpi must be positive")` (→ 64), nor `IOException("... already exists")`
(→ 73) from a generic `IOException` (→ 70). The error sites listed in the section 3 trigger columns
are therefore **retyped** to throw the app exception with the right `ErrorCategory`. Everything NOT
retyped falls through to the generic fallback below.

The CLI boundary (RegisterCommand/PipelineCommand, DespeckleCli/PipelineCli/TopdfCli) stops
catching `IOException`/`IllegalArgumentException` to a flat `RUNTIME_ERROR`; instead it calls the
shared mapper, prints `Error[NAME]: <userMessage>` (tate's `CliExceptionHandler` shape), and
returns `mapping.exitCode()`. `ParseException` (Commons CLI usage/arg errors) keeps mapping to
`2` (usage) — unchanged, and intentionally NOT routed through `ErrorCategory`.

`PiiSanitizer.maskAbsolutePaths` (absolute paths → `<path>`) and `FatalUncaughtHandler`
(OOM → exit 137) generalize unchanged from tate; the OOM exit code is now sourced from
`CommonErrorKind.OUT_OF_MEMORY.exitCode()` rather than a local `137` constant.

---

## 5. Throwable → kind fallback (the mapper's `asXxxException`)

Applied ONLY to throwables that are NOT already the app's domain exception (which carries its own
kind) and NOT a Commons CLI `ParseException` (→ usage exit `2`, handled at the CLI parse step).
Order matters; first match wins:

| throwable                  | → kind            | exitCode | severity |
|----------------------------|-------------------|----------|----------|
| app domain exception       | (its own kind)    | kind's   | kind's   |
| `IllegalArgumentException` | INVALID_PARAMETER | 64       | WARN     |
| `OutOfMemoryError`         | OUT_OF_MEMORY     | 137      | ERROR    |
| `IOException`              | INTERNAL          | 70       | ERROR    |
| anything else              | INTERNAL          | 70       | ERROR    |

(This matches tate's existing `asSpreadException` exactly: IllegalArgument→64, OOM→137,
IOException→70, else→70.)

**Why the retype in section 4 is mandatory:** the fallback keys on class only. Without retyping,
`IllegalArgumentException("Leptonica could not read image")` would land in INVALID_PARAMETER(64),
not IMAGE_UNREADABLE(65); `IOException("... already exists; pass --force")` would land in
INTERNAL(70), not OUTPUT_CONFLICT(73); `IOException("input PDF not found")` would land in
INTERNAL(70), not INPUT_NOT_FOUND(66). Only NATIVE_TOOL_FAILED(70) coincidentally equals the
IOException→INTERNAL(70) fallback — but it is still retyped so its distinct user message and
`clientFault=false` semantics are explicit. Genuine parameter validation
(PaperSize/dpi/outlier-ratio/speck-size, `ProcessOptions`) is deliberately LEFT as
`IllegalArgumentException` so the fallback's 64 is correct for it.

Apps may register extra throwable→kind rules in their own mapper (e.g. a future
`UncheckedIOException` unwrap), but the four rows above are the shared baseline.

---

## 6. Exit-code change: register / despeckle 0/1/2 → sysexits

This is a deliberate, user-approved uplift. Previously **both** apps returned only:
`0` OK, `1` RUNTIME_ERROR (any IOException/IllegalArgumentException/runtime), `2` USAGE_ERROR
(Commons CLI ParseException). After this spec:

- `0` unchanged (success).
- `2` unchanged (Commons CLI usage/parse errors — NOT routed through `ErrorCategory`).
- the former blanket `1` is replaced by the specific sysexits codes:
  `64` (bad value), `65` (image unreadable), `66` (input not found), `70` (internal /
  native-tool failure), `73` (output conflict), `137` (OOM).
- `1` is effectively retired for these two apps (no kind maps to it). `register`'s `ExitCodes`
  and `despeckle`'s `ExitCodes` (`OK/RUNTIME_ERROR/USAGE_ERROR`) are superseded by
  `ErrorCategory.exitCode()` + the usage-error `2` path.

### Callers to update

Grepped `*.sh`, `justfile`, `compose.yml`/`docker-compose.yml`, `lefthook.yml`, `rewrite.yml`,
and `.github/workflows/*.yml` across the monorepo:

- **No shell / justfile / compose / CI caller branches on `register` or `despeckle` exit codes.**
  The only `exit N` / `$?` occurrences are unrelated formatter recipes in the per-app justfiles
  (`spotlessApply && exit 0 … exit 1`, etc.) and a `MISSING; exit 1` tool-presence check — none
  inspect the apps' own exit status. No CI step asserts a specific app exit code.
- **Tests are the real consumers.** The existing test classes pin the OLD model and MUST be
  rewritten to this spec (they are the "later test rewrites" this doc governs):
  - `register/observability/.../ExceptionMapperTest.java` (asserts the 0/1/2 mapping)
  - `despeckle/observability/.../ExceptionMapperTest.java` (asserts the 0/1/2 mapping)
  - the app/CLI-level tests that assert `ExitCodes.RUNTIME_ERROR == 1` for IO/validation failures
  - tate's `ExceptionMapperTest` / `CliExceptionHandlerTest` only need adjustment if `ErrorKind`'s
    refactor to implement `ErrorCategory` changes their construction (exit codes/messages are
    unchanged for tate).

Action for downstream: when adding any future shell/CI consumer, treat `1` as "unspecified
failure" and branch on the sysexits codes instead.
