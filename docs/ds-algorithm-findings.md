# DS / Algorithm Modernization — Comprehensive Action List (Phase 4)

Exhaustive synthesis of the adversarially-verified DS/algorithm reviews (REGISTER,
DESPECKLE, TATE, CROSS-CUTTING) into one prioritized, parity-checkable plan. Goal:
propose **every genuine** smart/modern improvement — thoroughly — while never
regressing an intentional design or numeric parity. Overlapping/cross-referenced
findings are merged into single entries; every CUT is recorded with why (auditability).

Legend:
- **Neutral** = behavior-neutral; existing tests are the regression gate.
- **Changing** = behavior-changing; the tradeoff and the tests/ArchUnit rules that
  must be adjusted are named explicitly.

---

## 1. STATUS

### Already landed (do NOT re-propose)
| Id | What | Commit |
|----|------|--------|
| **R1** | Shared `Medians.upperMedian(int[]/long[])` in `:shared:kernel`; the four hand-rolled `clone/sort/[len/2]` copies (Reference, RegistrationService, Residuals, Charts) delegate; even-count tie-break test added. `RuntimeBenchmark.median` (averaging) deliberately excluded. | `559bed3` |
| **R2** | `Reference`: three observation passes collapsed to one terminal collect; one-pass x/y/w/h component extraction; Frankenbox + sort-independence preserved. | `559bed3` |
| **R3** | `PaperSize.displayName()` → exhaustive `switch` over the sealed `Standard\|Custom` (no `default`). | (R3) |
| **R4** | `RegisterOptions` adopts kernel `Validators.requirePositive` for the dpi check (byte-identical message); the `outlierRatio ∈ (0,1]` range check left alone. | (R4) |
| **R5** | Despeckle `ProcessOptions`: `int imageDpi` 0-sentinel replaced by `Optional<Resolution>`; `0 -> empty` mapped once at the FFM boundary; `ProcessOptionsTest` rewritten in lock-step (numeric outputs unchanged). | `6c1f9a1` / `152ad33` |

### Already planned — pending (do NOT re-derive; assumed to land)
| Id | What | Class |
|----|------|-------|
| **R6** | TATE: collapse the pagination Strategy hierarchy to ONE exhaustive-`switch` class. | HIGH / Changing (structure) |
| **R7** | TATE: `SpreadLayout` (+ `PagePairSpec.Single.half`) → tagged `domain.exception.Validators`. | MEDIUM / Changing (exception type) |

R6/R7 are carried verbatim below (Section 2) so the ranked list is complete, but their
design and tradeoffs are taken as already-decided — not re-litigated here.

---

## 2. Ranked KEEP list (everything we will implement)

Eight findings. Ordered by value (HIGH → LOW), then by blast radius. R6/R7 are the
pending pre-decided items; R8 and the `MJ*`/`O1` items are the newly-verified additions.

### R6 — TATE: collapse the pagination Strategy hierarchy — **HIGH / Changing (structure)**
*(pending; carried verbatim from the prior plan — do not re-derive)*
- **Location:** `tate-yoko-pdf/...` pagination package (`PaginationStrategy`,
  `StandardPagination`, `CoverSinglePagination`, `LeadingBlankPagination`, `Paginations`,
  `PaginationStrategyFactory`).
- **Target (variant b):** ONE class with a static
  `List<PagePairSpec> paginate(FirstPageMode mode, int totalPages)` holding a single
  exhaustive `switch` over `FirstPageMode` that builds the per-mode prefix
  (STANDARD: none; COVER: leading `Single(0)`; LEADING_BLANK: trailing `Single(0, TRAILING)`)
  then calls the shared `pairFrom`. Relocate `requirePages` + `pairFrom` into that class.
  **Reject variant (a)** (enum-with-behavior `mode.paginate(n)` on `FirstPageMode`): it
  pulls pagination logic + the `Validators` dependency onto a `domain.model` enum and lets
  any layer call it — keep `FirstPageMode` a pure data enum.
- **Deletes (5 + factory):** the five strategy/`Paginations` classes + `PaginationStrategyFactory`.
- **Fold-in doc fix:** `PaginationStrategy.java:19` javadoc says `totalPages >= 0` but
  `requirePages` enforces `> 0` (throws `PDF_INVALID_PAGE` for 0). Carry the corrected
  `@param {@code >= 1}` onto the new surviving entry point (do not edit a deleted file).
- **behaviorNeutral:** output unchanged; structurally changing (deletions + ArchUnit).
- **Tradeoffs (carry verbatim):**
  - **ArchUnit:** DELETE `strategyInstantiatedOnlyByApplication`
    (`tate-yoko-pdf/app/.../architecture/LayerDependencyTest.java:49`). It encodes a
    deliberate intent (strategy selection = application concern); variant (b) keeps a single
    application chokepoint that partially preserves it, but the enforced rule goes away.
  - **Re-point 7 test files:** `StandardPaginationTest`, `CoverSinglePaginationTest`,
    `LeadingBlankPaginationTest`; jqwik `StandardPaginationProperties`,
    `CoverSinglePaginationProperties`, `LeadingBlankPaginationProperties`; and
    `PaginationStrategyFactoryTest`. `FirstPageModeProperties` and the Calculator tests
    survive untouched. Output unchanged; re-pointed parity tests are the gate.

### R7 — TATE: `SpreadLayout` (+ `PagePairSpec.Single.half`) tagged null-validation — **MEDIUM / Changing (exception type)**
*(pending; carried verbatim from the prior plan — do not re-derive)*
- **Location:** `tate-yoko-pdf/domain/.../model/SpreadLayout.java:14-18` and
  `tate-yoko-pdf/domain/.../model/PagePairSpec.java:24`.
- **Fix:** route each field through the app's tagged
  `domain.exception.Validators.requireNonNull(value, ErrorKind.INVALID_PARAMETER, "name")`
  (SpreadException + ErrorKind), naming the offending field and carrying an exit code —
  matching every other tate-yoko record (`SpreadSpec`, `PageDimension`, `PagePairSpec`).
  Convert `PagePairSpec.Single.half` (`Objects.requireNonNull(half, "half")`, line 24)
  the same way for consistency (low individual value; rides along — drop if it expands scope).
  **Rejected** the `Objects.requireNonNull` alternative: it would add NPE as a *third* idiom
  rather than converge on the domain's existing tagged vocabulary.
- **behaviorNeutral:** false — today `SpreadLayout` throws `IllegalArgumentException`
  ("All fields must be non-null"); after, `SpreadException`.
- **Tradeoff / gate:** `SpreadLayoutTest` asserts `isInstanceOf(IllegalArgumentException.class)`
  at lines **35/42/52** — these MUST be updated to the new exception type. That update is the
  parity cost of fixing the inconsistency, not a regression.

### R8 — REGISTER: dedup the measure-then-rotate findSkew on the deskew path — **MEDIUM / Neutral**
- **Location:** `register/infrastructure/.../registrar/Deskewer.java:48-81` +
  `register/infrastructure/.../registrar/LeptonicaPageRegistrar.java:59,69`.
- **What:** on the default diagnostic+deskew path (`recordSkew && options.deskew()`),
  `LeptonicaPageRegistrar.analyze` line 59 calls `Deskewer.measureSkew(page)` (which runs
  `rotateOrth(1)+findSkew`), then line 69 calls `Deskewer.deskew(page)` whose first act
  (lines 49-50) is the SAME `rotateOrth(1)+measureRotated(findSkew)`. One native 90° rotation
  plus one `pixFindSkew` run per page are genuinely computed twice and the second result
  discarded. `pixFindSkew` is deterministic on identical pixels, so merging to one shared
  measurement is **numerically bit-identical** — a real cold-path (once/page) efficiency +
  API-clarity win, not churn.
- **Fix:** add an *additive* combined entry point (e.g. `record DeskewResult(Pix page,
  SkewEstimate estimate)` returned by `deskewWithEstimate(page)`) that performs
  `rotateOrth(1)+measureRotated` ONCE; `analyze` reuses that single estimate for the `Skew`
  diagnostic on the deskew branch instead of a separate `measureSkew` call.
  **CRITICAL:** keep the standalone `measureSkew` for the `!options.deskew()` branch at
  line 59 (only a measurement is wanted there, no deskew runs). After the change there must
  be **exactly one** `findSkew` call on the deskew+diagnostic path, with `correctable()`
  still the sole rotation gate. The merge strengthens the Deskewer's "one measurement drives
  both the diagnostic and the gate" invariant (one `findSkew` now literally feeds both).
- **behaviorNeutral:** true. Gate: existing register infrastructure tests.

### MJ1 — REGISTER: `toObservations` Optional-accumulate → `flatMap` (two modules) — **MEDIUM / Neutral**
- **Location:** `register/application/.../RegistrationService.java:232-244` **AND**
  `register/runner/.../Runner.java:194-206` — the helper is byte-identical in **both modules**.
- **What:** two byte-identical Optional-accumulate loops
  (`for(page) page.analysis().detection().ifPresent(det -> observations.add(new
  PageObservation(page.index(), page.parity(), det.column())))`). Textbook
  `Optional.stream()`/`flatMap` site; encounter order preserved by `flatMap`, result
  read-only downstream, so `ArrayList -> toList()` is safe.
- **Fix:**
  `pages.stream().flatMap(p -> p.analysis().detection().stream().map(det -> new
  PageObservation(p.index(), p.parity(), det.column()))).toList()`.
- **NOTE:** if a later DS-phase dedup lifts the duplicated `toObservations` into ONE shared
  helper, this rewrite should ride that move rather than land twice in two files.
- **behaviorNeutral:** true. Gate: existing application + runner tests.

### MJ8 — REGISTER: `DiagnosticReport` nullable-accumulate → `filter`→`toList` — **MEDIUM / Neutral**
- **Location:** `register/infrastructure/.../diag/DiagnosticReport.java:147-153`.
- **What:** nullable-field-accumulate loop (`List<Box> cols=new ArrayList<>(); for(p){Column
  c=p.column(); if(c!=null) cols.add(c.box());}`) then read-only (`cols.size()`,
  `stat(cols,...)`). Clean `filter`→`toList`; the non-Optional twin of MJ1. Order preserved,
  result read-only.
- **Fix:** `List<Box> cols = pages.stream().map(PageDiagnostic::column)
  .filter(Objects::nonNull).map(PageDiagnostic.Column::box).toList();`
- **CRITICAL — leave the sibling loop alone:** the rotated/found loop at 163-173 is
  **dual-accumulate** (one loop feeds both the `found` counter and the `rotated` list via two
  independent conditions), so it cannot collapse to a single `filter`→`toList`. Verified
  correctly excluded.
- **behaviorNeutral:** true. Gate: existing diagnostic tests.

### O1 / DUP1 — CROSS: extract register's PDF output naming to `:domain` (mirror despeckle) — **MEDIUM / Neutral**
*(O1 and DUP1 are the SAME finding, cross-referenced in two reports — one ranked entry.)*
- **Location:** `register/application/.../PdfBatchService.java:134-141` (`outputName` + inline
  `stripPdf`) vs despeckle/domain `.../service/PdfOutputNaming.java:25-51`.
- **What:** register's `PdfBatchService.outputName` is byte-identical pure logic to despeckle's
  already-extracted, domain-level, unit-tested `PdfOutputNaming.outputName`. Register HAS a
  `domain/service` package (`Reference`, `ProjectionProfile`, `TransformPlanner` live there),
  so the structural home exists. The honest value is lifting **pure naming logic out of
  `:application` into `:domain`** for structural symmetry with despeckle's sibling app, plus a
  filesystem-free domain-level test — NOT "fixes drift" (the fix deliberately keeps two classes
  for the island boundary, so the two sources of truth remain by design).
- **Fix:** mirror despeckle's `PdfOutputNaming` as a register-domain service
  (`register/domain/.../service/PdfOutputNaming`) and have `PdfBatchService.outputName`
  **DELEGATE** to it. Do **NOT** physically share one class across the two app islands (zero
  cross-app module edges; kernel is deliberately Path/IO-free so it cannot host a Path-typed
  helper).
- **Correction to the source reports:** the DUP1 `parityRisk` claim that register "omits
  despeckle's `Objects.requireNonNull` guard" is **factually wrong** — register line 135 is
  `Objects.requireNonNull(input.getFileName())`, identical to despeckle line 26; no NPE-timing
  risk exists.
- **behaviorNeutral:** true **ONLY IF** `PdfBatchService.outputName` delegates and the existing
  `PdfBatchServiceTest` (lines 46-64, which calls `PdfBatchService.outputName`) is left in place
  to gate. If the test is instead moved/repointed to the new domain class, behaviorNeutral
  becomes **false** (note the test relocation in that case). Recommended: keep the existing test
  in place; add a separate domain-level test for the new class.

### MJ3 — REGISTER: `CliSupport.parseEnum` valid-values builder → stream-join — **LOW / Neutral**
- **Location:** `register/app/.../cli/CliSupport.java:127-133` (`parseEnum`).
- **What:** hand-rolled `StringBuilder` loop over `type.getEnumConstants()` that lowercases each
  name and comma-joins them for the "valid values are: ..." error message.
- **Fix:** `Arrays.stream(type.getEnumConstants()).map(c -> c.name().toLowerCase(Locale.ROOT))
  .collect(Collectors.joining(", "))`. Same string; the `valueOf` happy path and the thrown
  message format are unchanged.
- **behaviorNeutral:** true. Gate: existing CLI tests.

### MJ2 — DESPECKLE: HTML `<head>`/`<style>` literal `.append()` run → text block — **LOW / Changing (bytes)**
- **Location:** `despeckle/infrastructure/.../report/HtmlReporter.java:262-287` **AND**
  `HtmlBatchReporter.java:57-68`.
- **What:** the leading run of pure-literal `<head>`/`<style>` `.append()` calls (no
  interpolation) is canonical text-block material, and the text-block category is otherwise
  empty in an HTML-emitting codebase.
- **behaviorNeutral:** **false** — a text block adds per-line newlines/indentation, so the
  emitted HTML **bytes** differ from the intentionally minified CSS string. Frame as
  *behavior-equivalent HTML*, NOT byte-identical.
- **Why it survives:** both test gates are substring-only (`HtmlReporterTest` asserts
  `contains("despeckle report")` + `contains("class=\"warn\"")` at lines 41-42;
  `HtmlBatchReporterTest` asserts `contains` of aggregate/link substrings) and neither asserts
  whitespace or exact bytes. **If a byte-exact golden/snapshot is ever added, this breaks.**
- **Fix:** replace only the leading run of pure-literal `.append()` calls with a single
  `"""..."""` text block; keep the dynamic counts (`rows.size()`, `totalRemoved`, `books.size()`)
  as the trailing `append()`/`formatted` tail. Apply the rule consistently: multi-line STATIC
  markup → text block; short interpolated captions (`CorpusOverlayRenderer`, `RemovedHeatmap`)
  stay as concatenation. Confirm the substring-only gates before landing.

---

## 3. LEAVE-ALONE (legitimate non-changes — changing them would regress)

These are deliberately preserved. Each was confirmed against the source. Several were
**re-raised this round and re-confirmed CUT** — noted as such for auditability (the value of
the re-raise is the second confirmation, not a second action).

### Task-specified leave-alones (changing would regress quality/parity)
- **Register `Reference` per-component "Frankenbox" median.** Each of x/y/w/h is the median of
  *that component taken separately* across pages. Do NOT "simplify" to one median `Box` picked
  by area — that changes behavior. The per-component extraction stays in the domain; only the
  array-core median moved to kernel (R1, done).
- **Despeckle `PdfListingParser` argmax tie-break** (`PdfListingParser.java:66-74`). The explicit
  insertion-ordered loop deliberately mirrors `stamp-dpi.py`'s `Counter.most_common` (first-seen
  tie-break + non-positive fallback to `DEFAULT_DPI`). A `Collections.max`/stream rewrite obscures
  the tie-break and risks Python-parity drift. Low value + parity risk + no-churn → **leave alone.**
  *(CROSS `pdflistingparser-argmax-modernize` proposed `Collections.max`; DESPECKLE said leave —
  resolved to leave. Conflict recorded.)*
- **Intentional Leptonica magic-constant duplication** (`LeptonicaPageCleaner.java:37-56`:
  `CONN_8`, `L_SELECT_*`, `IFF_*`). Documented and intentional — the imaging island keeps them
  package-private. **Leave alone.**
- **`RuntimeBenchmark.median`** — averages the two middle elements (true median, NOT upper-median):
  a *different algorithm*. Folding it into the shared `Medians.upperMedian` would silently change
  behavior. Exclusion is load-bearing (and already honored in R1).

### Prior-plan leave-alones (still valid)
- **`OutputFormat.extension()` `@Nullable` sentinel** (`OutputFormat.java:17`). DESPECKLE rates LOW
  and does not recommend it over R5. JSpecify `@Nullable` already makes absence explicit and
  type-checked; the sole caller (`DespeckleService.mirrorDestination():228-230`) handles it in one
  combined guard with another null, which `Optional` would force to split awkwardly. **Dropped.**
- **optional-vs-nullable modeling inconsistency** (`PageAnalysis` / `RegisterOptions`). Stylistic;
  broad, churny, not a DS/algorithm win. Out of scope for a no-churn pass. **Dropped.** (R4 adopted
  the kernel validator on `RegisterOptions` without touching its Optional/Nullable modeling.)

### Re-raised this round and re-confirmed CUT (auditability)
- **`solidInk` hole-filling / `thick.or(thickHoles)` operand**
  (`LeptonicaPageCleaner.java:182-187`). *(= prior `A3`; re-raised as
  `DS-DESPECKLE-thick-operand-reraise`, re-confirmed CUT.)* The set-algebra holds: `opened()` is
  anti-extensive so `thick ⊆ pix`'s black pixels; `smallHoles` returns a white subset of
  `inverted(pix)`; so in `fillHoles`, `holes.and(thick.or(thickHoles)) == holes.and(thickHoles)` —
  the `thick` operand is inert **for this caller's intersection only**. But that inertness is an
  accident of `fillHoles`'s intersection, not a property of `solidInk`. `solidInk` is a javadoc'd
  named abstraction ("pix reduced to ink thicker than `strokeThickness`, with its pin-holes
  filled"); the `thick` term is precisely what makes the value BE the solid ink. Dropping it
  falsifies both the method name and its javadoc to save one cold-path `Pix` alloc the task warns
  off. The finding itself recommends NO change. **Leave as-is** (parity tests
  `fillHolesClosesPinHoleInsideStroke`, `fillHolesSparesTheGapInsideAThinWalledGlyph` continue to
  gate).
- **`SpreadLayout` sealed-hierarchy / record split + `SpreadService` Optional round-trip**
  (`SpreadLayout.java:12-13`, `SpreadService.java:101`). *(= prior "SpreadService Optional
  round-trip"; re-raised as `TATE-sealed-SpreadLayout-record-split-REJECTED`, re-confirmed CUT.)*
  `SpreadService` is the sole reader; the exhaustive `switch` on `PagePairSpec (Pair\|Single)` at
  `SpreadService:88` already dispatches, and inside the `Pair` arm `secondPosition().orElseThrow()`
  is a safe assertion, not a smell. A sealed `SingleLayout|PairLayout` split is YAGNI for a single
  consumer — type ceremony for no readability/safety gain. **Keep one record with
  `Optional<LayoutPosition> secondPosition`.**
- **Corpus-walk / mirror-dest IO cluster duplication**
  (`RegistrationService.java:466-516` vs `DespeckleService.java:198-239`: `prepareOutputDir`,
  `collectFiles`, `mirrorDestination`, `stripExtension`; `O2`/`DUP2`, re-confirmed CUT). Genuinely
  byte-identical cross-app dup (incl. the exact error string `output directory "+dir+" is not
  empty; pass --force to overwrite` and `sorted(Path::toString)`). But this is filesystem
  ORCHESTRATION (`Files`, `PathMatcher`, `FileSystems`), not pure-domain; the kernel is
  deliberately Path/IO-free and cannot host it. A real share needs a NEW `:shared:io` module — a
  structural decision beyond a DS/algorithm pass and gated on breaking the intentional island
  independence; `:application` is E2E-tested (not unit-floored), lowering payoff. **No change this
  pass.** If a `:shared:io` module is ever created, the `collectFiles`/`listPdfs` pairs and their
  `sorted(Path::toString)` ordering + glob-on-file-name semantics are the parity contract to
  preserve verbatim. The recursive-walk vs flat-list semantics differ between the two services, so
  even an *intra-app* merge is not consolidatable.

### TATE geometry baselines (validated clean — nothing to apply)
- **`SpreadLayoutCalculator` geometry** (`SpreadLayoutCalculator.java:17-56` + `PageDimension`,
  `SpreadSpec`, `SpreadLayout`, `LayoutPosition`). The cleanest O(1) formulation: `calculate()`
  invokes the shared `place(direction,half,bounds,page)` twice + `calculateSingle()` once; the
  bounding box is the elementwise `PageDimension.max`; `spreadSpec()` always emits `widthPt()*2`
  so single and paired share one frame. **Leave exactly as-is** (`SpreadLayoutCalculatorTest`
  pins it). *(`TATE-GEOM-validated-leave-alone`, CUT = no improvement proposed.)*
- **`onRightHalf` XNOR** (`SpreadLayoutCalculator.java:55`):
  `(direction == ReadingDirection.RTL) == (half == SpreadHalf.LEADING)` is the canonical encoding
  of "these two binary facts agree" — strictly shorter/clearer than a 4-arm or nested switch. Java
  has no tuple switch; a switch rewrite is pure ceremony for identical output. Exhaustiveness is
  already free (two 2-value enums, total result). **Do NOT introduce a switch.**
  *(`TATE-onRightHalf-switch-REJECTED`, CUT.)*
- **Pure-data direction/half/side enums** (`ReadingDirection`, `SpreadHalf`, `OpeningSide`,
  `FirstPageMode`). Moving the side↔direction placement logic onto the enums
  (`direction.placesOnRight(half)`) would be an architecture/layer regression, dissolving the
  layer boundary the ArchUnit rules guard. Keep the enums pure data; keep `onRightHalf` inside the
  calculator service. *(`TATE-enum-behavior-mapping-REJECTED`, CUT.)*

---

## 4. Implementation groups (independent, parity-checkable commits)

R1–R5 are **done** and are not implementation groups. Groups are clustered by app/area so each
is an independent, parity-checkable commit. **Behavior-NEUTRAL groups are separated from
behavior-CHANGING ones.** Order is free except where noted.

### Behavior-NEUTRAL groups (existing tests are the gate; no test/ArchUnit edits)

- **G1 — register/infrastructure: Deskewer dedup (R8).** Add the additive
  `deskewWithEstimate` combined entry point in `Deskewer`; have `LeptonicaPageRegistrar.analyze`
  reuse the one estimate on the deskew branch; keep standalone `measureSkew` for the
  `!options.deskew()` branch. Exactly one `findSkew` on the deskew+diagnostic path afterward.
  *Gate:* existing register-infrastructure tests (bit-identical output).

- **G2 — register: `toObservations` → `flatMap` (MJ1).** Spans **two modules** —
  `RegistrationService` (`:application`) and `Runner` (`:runner`) — the helper is byte-identical
  in both. Either land as one logical change touching both files, or, if a future dedup lifts
  `toObservations` into a single shared helper, ride that move instead of editing two copies.
  *Gate:* existing application + runner tests.

- **G3 — register/infrastructure: `DiagnosticReport` filter→toList (MJ8).** Collapse the
  nullable-accumulate `cols` loop; leave the dual-accumulate rotated/found loop (163-173) untouched.
  *Gate:* existing diagnostic tests. *(Could ride with G1 as a single register/infrastructure
  commit if preferred; kept separate here for an independent parity check.)*

- **G4 — register/app: `CliSupport.parseEnum` stream-join (MJ3).** Replace the `StringBuilder`
  valid-values loop with `Arrays.stream(...).map(...).collect(joining(", "))`. Same message.
  *Gate:* existing CLI tests.

- **G5 — register: lift `PdfOutputNaming` to `:domain` (O1/DUP1).** Mirror despeckle's
  `PdfOutputNaming` as `register/domain/.../service/PdfOutputNaming`; `PdfBatchService.outputName`
  delegates. Do NOT share one class across islands; do NOT route to kernel (Path-typed).
  **Keep `PdfBatchServiceTest` (lines 46-64) in place** so it gates the delegating method (this is
  what keeps the change behavior-neutral); add a separate filesystem-free domain test for the new
  class. *Gate:* existing `PdfBatchServiceTest` + new domain test.

> The register-side neutral groups (G1–G5) are mutually independent; G1+G3 are both
> register/infrastructure and may be combined into one commit if a single infra parity run is
> preferred.

### Behavior-CHANGING groups (each names the tests/ArchUnit rules it must adjust)

- **G6 — despeckle: HTML text block (MJ2).** **behaviorNeutral=false** (emitted HTML bytes change:
  newlines/indentation vs minified CSS). Replace only the leading literal `.append()` run in
  `HtmlReporter` and `HtmlBatchReporter` with a `"""..."""` text block; keep the dynamic tail as
  `append()`. **Tests to verify (not change):** `HtmlReporterTest` (substring asserts at lines
  41-42) and `HtmlBatchReporterTest` (aggregate/link substring asserts) — both are substring-only
  and continue to pass. **Warning:** if a byte-exact golden/snapshot is ever added, this breaks;
  do not pair this commit with introducing one.

- **G7 — tate: collapse the pagination Strategy hierarchy (R6).** New single-entry pagination class
  (relocate `requirePages`/`pairFrom`); delete the 5 strategy files + `PaginationStrategyFactory`;
  fold the `>= 1` doc fix onto the surviving entry point. **ArchUnit:** delete
  `strategyInstantiatedOnlyByApplication` (`tate-yoko-pdf/app/.../architecture/LayerDependencyTest.java:49`).
  **Tests:** re-point 7 files (`StandardPaginationTest`, `CoverSinglePaginationTest`,
  `LeadingBlankPaginationTest`; jqwik `StandardPaginationProperties`,
  `CoverSinglePaginationProperties`, `LeadingBlankPaginationProperties`;
  `PaginationStrategyFactoryTest`) at the new entry point; `FirstPageModeProperties` and the
  Calculator tests survive. Output unchanged; re-pointed parity tests are the gate.

- **G8 — tate: `SpreadLayout` tagged validation (R7).** Route `SpreadLayout` (and same-theme
  `PagePairSpec.Single.half`) through the tagged `domain.exception.Validators`. **Tests:** update
  `SpreadLayoutTest` lines **35/42/52** from `IllegalArgumentException` to `SpreadException`. No
  ArchUnit change. Behavior-changing (exception type); the test update is the parity cost.

### Notes on grouping
- G7 and G8 are both tate but independent (different files, different gates) — keep as two commits.
- No group requires a verify-then-act loop: the prior plan's only such item (`A3`/solidInk) was
  verified analytically to require **no change** and is in the leave-alone section.
- Build/parity runs are Docker-only: `docker compose run --rm dev ./gradlew :<module>:test`
  (and the ArchUnit `LayerDependencyTest` for G7).

---

*Verified findings are from the adversarial REGISTER/DESPECKLE/TATE/CROSS reviews. The REGISTER
input was clipped mid-MJ3; MJ4–MJ7 were not forwarded (almost certainly unforwarded CUTs) and are
deliberately NOT fabricated here. The register KEEP set is R8 + MJ1/MJ2(despeckle)/MJ3/MJ8 + O1.*
