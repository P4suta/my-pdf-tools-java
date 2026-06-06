<script lang="ts">
  import { fade, fly } from "svelte/transition";
  import {
    type ConversionOptions,
    type Direction,
    type FirstPage,
    getStatus,
    probe,
    type ProgressEvent,
    resultUrl,
    sha256Hex,
    streamProgress,
    submitJob,
  } from "./api";

  type Phase = "idle" | "uploading" | "running" | "done" | "failed";
  type StepKey = "extract" | "despeckle" | "register" | "spread";
  type StepStatus = "pending" | "active" | "done" | "failed";

  interface Step {
    key: StepKey;
    label: string;
    status: StepStatus;
    done: number;
    total: number;
    indeterminate: boolean; // extract reports no per-page events, so its bar can't be determinate
  }

  const STEP_LABELS: Record<StepKey, string> = {
    extract: "抽出",
    despeckle: "ノイズ除去",
    register: "整列",
    spread: "製本",
  };

  // Error-kind → Japanese. The backend and shared kernel are presentation-free: they send only the
  // stable kind token (e.g. "OUTPUT_CONFLICT"); the web UI owns its Japanese text here, exactly as
  // the CLI owns its English catalog. An unknown kind falls back to the backend detail, then a
  // generic line — so a newly added kind degrades gracefully rather than showing a raw token.
  const ERROR_KIND_JA: Record<string, string> = {
    INVALID_PARAMETER: "入力値が不正です。",
    OUTPUT_CONFLICT: "出力先が既に存在します。",
    OUT_OF_MEMORY: "メモリが不足しました。ページ数の少ない PDF でお試しください。",
    INTERNAL: "予期しないエラーが発生しました。",
    INPUT_NOT_FOUND: "入力ファイルが見つかりません。",
    IMAGE_UNREADABLE:
      "画像を読み込めませんでした。対応していない形式か、ファイルが破損している可能性があります。",
    NATIVE_TOOL_FAILED: "内部ツールの実行に失敗しました。しばらくして再度お試しください。",
    PDF_CORRUPTED: "PDF を読み込めませんでした。ファイルが破損している可能性があります。",
    PDF_PASSWORD_PROTECTED: "PDF がパスワードで保護されているため処理できません。",
    PDF_NOT_FOUND: "PDF ファイルが見つかりません。",
    PDF_INVALID_PAGE: "PDF のページ指定が不正です。",
    PDF_WRITE_FAILED: "出力 PDF の書き出しに失敗しました。",
  };

  function errorMessageJa(kind: string | null, detail: string | null): string {
    if (kind && ERROR_KIND_JA[kind]) {
      return ERROR_KIND_JA[kind];
    }
    return detail ?? "変換に失敗しました";
  }

  let file = $state<File | null>(null);
  let fileNote = $state<string | null>(null);
  let dragging = $state(false);
  let options = $state<ConversionOptions>({
    direction: "RTL",
    firstPage: "right",
    despeckle: true,
    register: true,
    deskew: true,
    scale: true,
    pdfA: false,
  });

  let phase = $state<Phase>("idle");
  let steps = $state<Step[]>([]);
  let jobId = $state<string | null>(null);
  let error = $state<string | null>(null);
  let errorKind = $state<string | null>(null);
  let queued = $state(false);
  let fromCache = $state(false); // served instantly from the result cache (upload skipped)
  let source: EventSource | null = null;

  // Liveness clock: `now` ticks once a second while a job is in flight, so the elapsed time and the
  // "last update" readout keep moving even when no progress event arrives (the extract step is
  // silent, and a slow or hung step would otherwise look identical to a fast one).
  let now = $state(Date.now());
  let startedAt = $state<number | null>(null);
  let lastEventAt = $state<number | null>(null);

  $effect(() => {
    if (phase !== "uploading" && phase !== "running") {
      return;
    }
    now = Date.now();
    const id = setInterval(() => {
      now = Date.now();
    }, 1000);
    return () => clearInterval(id);
  });

  const elapsedMs = $derived(startedAt === null ? 0 : Math.max(0, now - startedAt));
  const sinceEventMs = $derived(lastEventAt === null ? null : Math.max(0, now - lastEventAt));
  const allDone = $derived(steps.length > 0 && steps.every((s) => s.status === "done"));
  const stale = $derived(phase === "running" && sinceEventMs !== null && sinceEventMs > 20000);

  // Overall progress = average of per-step completion. A done step counts as full; the active step
  // contributes its page ratio (extract is indeterminate, so it contributes nothing to the bar; the
  // stepper and the elapsed clock carry the signal while it runs).
  const overall = $derived.by(() => {
    if (steps.length === 0) {
      return 0;
    }
    let acc = 0;
    for (const s of steps) {
      if (s.status === "done") {
        acc += 1;
      } else if (s.status === "active" && !s.indeterminate && s.total > 0) {
        acc += Math.min(1, s.done / s.total);
      }
    }
    return acc / steps.length;
  });
  const percent = $derived(Math.round(overall * 100));

  const statusWord = $derived(
    phase === "uploading"
      ? "アップロード中"
      : queued
        ? "順番待ち"
        : phase === "done"
          ? "完了"
          : phase === "failed"
            ? "失敗"
            : allDone
              ? "仕上げ中"
              : "製本中",
  );

  function fmtDuration(ms: number): string {
    const total = Math.floor(ms / 1000);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    const mm = String(m).padStart(2, "0");
    const ss = String(s).padStart(2, "0");
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  }

  function stepPercent(step: Step): number {
    if (step.total <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((step.done / step.total) * 100));
  }

  // The full milestone list is known up front from the submitted options (extract and spread always
  // run; despeckle/register are optional), so the stepper shows the whole pipeline from the start.
  function buildSteps(o: ConversionOptions): Step[] {
    const keys: StepKey[] = ["extract"];
    if (o.despeckle) {
      keys.push("despeckle");
    }
    if (o.register) {
      keys.push("register");
    }
    keys.push("spread");
    return keys.map((key) => ({
      key,
      label: STEP_LABELS[key],
      status: "pending" as StepStatus,
      done: 0,
      total: 0,
      indeterminate: key === "extract",
    }));
  }

  function stepOf(stage: string): Step | undefined {
    return steps.find((s) => s.key === stage);
  }

  function markFailedStep() {
    const target =
      steps.find((s) => s.status === "active") ?? steps.find((s) => s.status === "pending");
    if (target) {
      target.status = "failed";
    }
  }

  // Mirror the server's download name (JobController.downloadName): "<base>_book.pdf". Used as the
  // result URL's last segment so the browser names the download after the source book, not "result".
  function bookName(original: string): string {
    const base = original.replace(/\.pdf$/i, "");
    return `${base.trim() === "" ? "book" : base}_book.pdf`;
  }
  const downloadName = $derived(file ? bookName(file.name) : "book.pdf");

  function advance(event: ProgressEvent) {
    lastEventAt = Date.now();
    queued = false; // any progress event means the worker has picked the job up
    switch (event.type) {
      case "runStarted":
        for (const s of steps) {
          s.status = "pending";
          s.done = 0;
        }
        break;
      case "stageStarted": {
        let reached = false;
        for (const s of steps) {
          if (s.key === event.stage) {
            s.status = "active";
            reached = true;
          } else if (!reached && s.status !== "done") {
            s.status = "done";
            if (s.total > 0) {
              s.done = s.total;
            }
          }
        }
        break;
      }
      case "pageProcessed": {
        const s = stepOf(event.stage);
        if (s) {
          s.status = "active";
          s.total = event.total;
          s.done = Math.max(s.done, event.done); // pages finish out of order; never step back
        }
        break;
      }
      case "stageCompleted": {
        const s = stepOf(event.stage);
        if (s) {
          s.status = "done";
          if (s.total > 0) {
            s.done = s.total;
          }
        }
        break;
      }
      case "runCompleted":
        // pdfbook finished writing, but the server may still be reaping the subprocess; don't show
        // the result until the job is DONE, or the preview races a 409 not-ready response.
        for (const s of steps) {
          s.status = "done";
        }
        source?.close();
        if (jobId) {
          void finalize(jobId);
        }
        break;
      case "runFailed":
        markFailedStep();
        error = event.message;
        errorKind = event.kind;
        phase = "failed";
        source?.close();
        break;
    }
  }

  function delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // After `runCompleted`, poll the authoritative job status until the server has the result ready,
  // then reveal it. Bails if the run was superseded (reset, or a new job) while polling.
  async function finalize(id: string) {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (jobId !== id) {
        return;
      }
      try {
        const status = await getStatus(id);
        if (jobId !== id) {
          return;
        }
        if (status.state === "DONE") {
          phase = "done";
          return;
        }
        if (status.state === "FAILED") {
          error = status.errorMessage ?? "変換に失敗しました";
          errorKind = status.errorKind;
          markFailedStep();
          phase = "failed";
          return;
        }
      } catch {
        /* transient network error; keep polling */
      }
      await delay(300);
    }
    if (jobId === id) {
      error = "結果の確定待ちでタイムアウトしました。";
      phase = "failed";
    }
  }

  // Until the first progress event arrives the SSE stream is silent whether the job is queued behind
  // another conversion or already running (the extract step emits nothing). Poll the authoritative
  // status so the header can say "順番待ち" instead of sitting at a stalled-looking 0%.
  async function watchQueue(id: string) {
    for (let i = 0; i < 400; i++) {
      if (jobId !== id || lastEventAt !== null) {
        return;
      }
      try {
        const status = await getStatus(id);
        if (jobId !== id || lastEventAt !== null) {
          return;
        }
        if (status.state === "QUEUED") {
          queued = true;
        } else if (status.state === "RUNNING") {
          queued = false;
        } else if (status.state === "DONE") {
          phase = "done";
          return;
        } else if (status.state === "FAILED") {
          error = status.errorMessage ?? "変換に失敗しました";
          errorKind = status.errorKind;
          markFailedStep();
          phase = "failed";
          return;
        }
      } catch {
        /* transient network error; keep polling */
      }
      await delay(1500);
    }
  }

  async function start() {
    if (!file) {
      return;
    }
    source?.close();
    error = null;
    errorKind = null;
    queued = false;
    fromCache = false;
    steps = buildSteps(options);
    startedAt = Date.now();
    now = Date.now();
    lastEventAt = null;
    phase = "uploading";
    try {
      // Fast path: if this exact file + options is already cached, the server returns a ready job and
      // we skip uploading the file entirely. Falls through to a normal upload when local hashing is
      // unavailable (an insecure, non-HTTPS context) or the result is not cached.
      const sha = await sha256Hex(file);
      if (sha) {
        const cachedId = await probe(sha, options, file.name);
        if (cachedId) {
          jobId = cachedId;
          for (const s of steps) {
            s.status = "done";
          }
          fromCache = true;
          phase = "done";
          return;
        }
      }
      const id = await submitJob(file, options);
      jobId = id;
      phase = "running";
      source = streamProgress(id, advance, () => {
        /* transient; the server replays on reconnect */
      });
      void watchQueue(id);
    } catch (cause) {
      error = cause instanceof Error ? cause.message : String(cause);
      phase = "failed";
    }
  }

  function setFile(picked: File | null) {
    if (picked?.type && picked.type !== "application/pdf") {
      fileNote = "PDFファイル (application/pdf) を選んでください。";
      return;
    }
    fileNote = null;
    file = picked;
  }

  function onFile(event: Event) {
    setFile((event.currentTarget as HTMLInputElement).files?.[0] ?? null);
  }

  function onDragOver(event: DragEvent) {
    event.preventDefault();
    dragging = true;
  }

  function onDragLeave() {
    dragging = false;
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    dragging = false;
    setFile(event.dataTransfer?.files?.[0] ?? null);
  }

  function reset() {
    source?.close();
    phase = "idle";
    steps = [];
    jobId = null;
    error = null;
    errorKind = null;
    queued = false;
    fromCache = false;
    startedAt = null;
    lastEventAt = null;
  }
</script>

<div class="shell" data-phase={phase}>
  <header class="topbar">
    <span class="logo" aria-hidden="true">📖</span>
    <div class="brand">
      <h1>pdfbook</h1>
      <p class="tagline">自炊した縦書き本のスキャンPDFを、右綴じの見開きに製本</p>
    </div>
  </header>

  <main class="screen">
    {#if phase === "idle"}
      <!-- Setup: the only phase where the controls exist. -->
      <section class="view" in:fade={{ duration: 180 }} out:fade={{ duration: 140 }}>
        <form
          class="panel"
          class:has-file={!!file}
          onsubmit={(e) => {
            e.preventDefault();
            start();
          }}
        >
          <label
            class="dropzone"
            class:dragging
            class:filled={!!file}
            ondragover={onDragOver}
            ondragleave={onDragLeave}
            ondrop={onDrop}
          >
            <input type="file" accept="application/pdf" onchange={onFile} />
            <span class="dz-icon" aria-hidden="true">⬆</span>
            <span class="dz-title">{file ? file.name : "スキャンPDFをドロップ"}</span>
            <span class="dz-hint">
              {file ? "クリックで別のファイルに変更" : "またはクリックして選択 (application/pdf)"}
            </span>
          </label>
          {#if fileNote}
            <p class="field-error">{fileNote}</p>
          {/if}
          <!-- Options stay hidden until a file is picked: the empty state is just the picker. -->
          {#if file}
            <div class="setup-controls" in:fly={{ y: 10, duration: 180 }}>
              <fieldset>
                <legend>オプション</legend>
                <div class="grid">
                  <label class="field">
                    <span>綴じ方向</span>
                    <select
                      value={options.direction}
                      onchange={(e) => (options.direction = e.currentTarget.value as Direction)}
                    >
                      <option value="RTL">右綴じ (RTL)</option>
                      <option value="LTR">左綴じ (LTR)</option>
                    </select>
                  </label>
                  <label class="field">
                    <span>1ページ目</span>
                    <select
                      value={options.firstPage}
                      onchange={(e) => (options.firstPage = e.currentTarget.value as FirstPage)}
                    >
                      <option value="right">右始まり</option>
                      <option value="left">左始まり (先頭空白)</option>
                      <option value="cover">表紙単独</option>
                    </select>
                  </label>
                </div>
                <div class="checks">
                  <label class="check">
                    <input type="checkbox" bind:checked={options.despeckle} />
                    <span>ノイズ除去 (despeckle)</span>
                  </label>
                  <label class="check">
                    <input type="checkbox" bind:checked={options.register} />
                    <span>整列 (register)</span>
                  </label>
                  <label class="check">
                    <input type="checkbox" bind:checked={options.pdfA} />
                    <span>PDF/A-2b 準拠</span>
                  </label>
                </div>
              </fieldset>

              <button class="primary block" type="submit">製本する</button>
            </div>
          {/if}
        </form>
      </section>
    {:else if phase === "done" && jobId}
      <!-- Done: the section is the shared .view (a flex column), so the preview (flex: 1) is the one
           child that grows to fill the height; the bar sits above it. -->
      <section class="view" in:fade={{ duration: 240 }}>
        <div class="result-bar">
          <span class="ok"
            >✓ 製本が完了しました{fromCache
              ? "（キャッシュ）"
              : `（所要 ${fmtDuration(elapsedMs)}）`}</span
          >
          <span class="grow"></span>
          <a class="primary" href={resultUrl(jobId, downloadName)} target="_blank" rel="noopener">
            新規タブで開く
          </a>
          <button class="ghost" type="button" onclick={reset}>もう一冊</button>
        </div>
        <iframe class="preview" src={resultUrl(jobId, downloadName)} title="製本PDFプレビュー"
        ></iframe>
      </section>
    {:else}
      <!-- Work: the pipeline stepper shows the whole flow, current step, and liveness. The same view
           (with the failed step in red) shows on failure. -->
      <section class="view" in:fade={{ duration: 200 }}>
        <div class="run-head">
          <div class="run-status">
            <span class="status-word" class:is-failed={phase === "failed"}>{statusWord}</span>
            <span class="elapsed">{fmtDuration(elapsedMs)} 経過</span>
          </div>
          <div class="overall" class:indeterminate={percent === 0 && phase !== "failed"}>
            <div class="overall-fill" style:width={`${percent}%`}></div>
          </div>
          <div class="liveness" class:stale class:failed={phase === "failed"}>
            {#if queued}
              順番待ち（他の変換の完了を待っています）
            {:else if phase === "failed"}
              失敗しました
            {:else if sinceEventMs === null}
              準備中…
            {:else if sinceEventMs < 5000}
              <span class="dot" aria-hidden="true"></span> 処理中
            {:else}
              更新 {Math.floor(sinceEventMs / 1000)} 秒前{#if stale}
                ・ この工程は時間がかかることがあります（処理は継続中）{/if}
            {/if}
          </div>
        </div>

        <ol class="stepper">
          {#each steps as step (step.key)}
            <li
              class="step"
              class:active={step.status === "active"}
              class:done={step.status === "done"}
              class:failed={step.status === "failed"}
            >
              <span class="step-icon" aria-hidden="true">
                {#if step.status === "done"}✓{:else if step.status === "failed"}✕{:else if step.status === "active"}▶{:else}○{/if}
              </span>
              <div class="step-body">
                <div class="step-row">
                  <span class="step-name">{step.label}</span>
                  <span class="step-meta">
                    {#if step.status === "done"}
                      完了
                    {:else if step.status === "failed"}
                      失敗
                    {:else if step.status === "active"}
                      {#if step.indeterminate || step.total <= 0}
                        処理中…
                      {:else}
                        {step.done} / {step.total} ページ
                      {/if}
                    {:else}
                      待機中
                    {/if}
                  </span>
                </div>
                {#if step.status === "active"}
                  {#if step.indeterminate || step.total <= 0}
                    <div class="step-bar indeterminate">
                      <div class="step-fill"></div>
                    </div>
                  {:else}
                    <div class="step-bar">
                      <div class="step-fill" style:width={`${stepPercent(step)}%`}></div>
                    </div>
                  {/if}
                {/if}
              </div>
            </li>
          {/each}
        </ol>

        {#if phase === "failed"}
          <div class="errbox">
            <strong>{errorMessageJa(errorKind, error)}</strong>
            {#if errorKind}<p class="errkind">{errorKind}</p>{/if}
            <button class="ghost" type="button" onclick={reset}>戻る</button>
          </div>
        {/if}
      </section>
    {/if}
  </main>
</div>
