<script lang="ts">
import { fade } from "svelte/transition";
import {
  type ConversionOptions,
  type Direction,
  type FirstPage,
  getStatus,
  type ProgressEvent,
  resultUrl,
  streamProgress,
  submitJob,
} from "./api";

type Phase = "idle" | "uploading" | "running" | "done" | "failed";

interface Progress {
  fraction: number;
  label: string;
  stageCount: number;
  index: number;
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
let progress = $state<Progress>({ fraction: 0, label: "", stageCount: 0, index: 0 });
let jobId = $state<string | null>(null);
let error = $state<string | null>(null);
let source: EventSource | null = null;

const percent = $derived(Math.round(progress.fraction * 100));

// Mirror the server's download name (JobController.downloadName): "<base>_book.pdf". Used as the
// result URL's last segment so the browser names the download after the source book, not "result".
function bookName(original: string): string {
  const base = original.replace(/\.pdf$/i, "");
  return `${base.trim() === "" ? "book" : base}_book.pdf`;
}
const downloadName = $derived(file ? bookName(file.name) : "book.pdf");

function advance(event: ProgressEvent) {
  switch (event.type) {
    case "runStarted":
      progress = { fraction: 0, label: "準備中…", stageCount: event.stageCount, index: 0 };
      break;
    case "stageStarted":
      progress = {
        fraction: event.stageCount === 0 ? 0 : event.index / event.stageCount,
        label: `${event.stage}…`,
        stageCount: event.stageCount,
        index: event.index,
      };
      break;
    case "pageProcessed": {
      const stages = progress.stageCount === 0 ? 1 : progress.stageCount;
      const within = event.total === 0 ? 0 : event.done / event.total;
      progress = {
        ...progress,
        // The pages complete on a worker pool, so `done` arrives out of order (e.g. 2,1,4,3…);
        // clamp the bar to never step backward while still letting the label show the live page.
        fraction: Math.max(progress.fraction, (progress.index + within) / stages),
        label: `${event.stage} (${event.done}/${event.total})`,
      };
      break;
    }
    case "stageCompleted": {
      const stages = progress.stageCount === 0 ? 1 : progress.stageCount;
      progress = {
        ...progress,
        fraction: (progress.index + 1) / stages,
        label: `${event.stage} 完了`,
      };
      break;
    }
    case "runCompleted":
      // pdfbook finished writing, but the server may still be reaping the subprocess; don't show
      // the result until the job is actually DONE, or the preview races a 409 not-ready response.
      progress = { ...progress, fraction: 1, label: "仕上げ中…" };
      source?.close();
      if (jobId) {
        void finalize(jobId);
      }
      break;
    case "runFailed":
      error = event.message;
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

async function start() {
  if (!file) {
    return;
  }
  source?.close();
  error = null;
  phase = "uploading";
  progress = { fraction: 0, label: "アップロード中…", stageCount: 0, index: 0 };
  try {
    const id = await submitJob(file, options);
    jobId = id;
    phase = "running";
    source = streamProgress(id, advance, () => {
      /* transient; the server replays on reconnect */
    });
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
  progress = { fraction: 0, label: "", stageCount: 0, index: 0 };
  jobId = null;
  error = null;
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
      <section class="view setup" in:fade={{ duration: 180 }} out:fade={{ duration: 140 }}>
        <form
          class="panel"
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

          <button class="primary block" type="submit" disabled={!file}>製本する</button>
        </form>
      </section>
    {:else}
      <!-- Work: controls are gone; only progress, then the result, fills the screen. -->
      <section class="view work" in:fade={{ duration: 240 }}>
        {#if phase === "done" && jobId}
          <div class="result" in:fade={{ duration: 240 }}>
            <div class="result-bar">
              <span class="ok">✓ 製本が完了しました</span>
              <span class="grow"></span>
              <a
                class="primary"
                href={resultUrl(jobId, downloadName)}
                target="_blank"
                rel="noopener"
              >
                新規タブで開く
              </a>
              <button class="ghost" type="button" onclick={reset}>もう一冊</button>
            </div>
            <iframe
              class="preview"
              src={resultUrl(jobId, downloadName)}
              title="製本PDFプレビュー"
            ></iframe>
          </div>
        {:else if phase === "failed"}
          <div class="centered" in:fade={{ duration: 200 }}>
            <div class="errbox">
              <strong>変換に失敗しました</strong>
              <p>{error}</p>
              <button class="ghost" type="button" onclick={reset}>戻る</button>
            </div>
          </div>
        {:else}
          <div class="centered running" in:fade={{ duration: 200 }}>
            <div class="rnum">{percent}<span class="unit">%</span></div>
            <div class="bar wide"><div class="fill" style:width={`${percent}%`}></div></div>
            <p class="rlabel">{progress.label}</p>
          </div>
        {/if}
      </section>
    {/if}
  </main>
</div>
