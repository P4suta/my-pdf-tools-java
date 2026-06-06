<script lang="ts">
import {
  type ConversionOptions,
  type Direction,
  type FirstPage,
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

const busy = $derived(phase === "uploading" || phase === "running");
const percent = $derived(Math.round(progress.fraction * 100));

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
        fraction: (progress.index + within) / stages,
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
      progress = { ...progress, fraction: 1, label: "完了" };
      phase = "done";
      source?.close();
      break;
    case "runFailed":
      error = event.message;
      phase = "failed";
      source?.close();
      break;
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

function onFile(event: Event) {
  file = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
}
</script>

<main class="app">
  <h1>pdfbook</h1>
  <p class="lead">
    自炊した縦書き本のスキャンPDFを、ノイズ除去・整列して右綴じの見開きに製本します。
  </p>

  <section class="card">
    <label class="file">
      <input type="file" accept="application/pdf" disabled={busy} onchange={onFile} />
      <span>{file ? file.name : "スキャンPDFを選択…"}</span>
    </label>

    <fieldset disabled={busy}>
      <legend>オプション</legend>
      <label>
        綴じ方向
        <select
          value={options.direction}
          onchange={(e) => (options.direction = e.currentTarget.value as Direction)}
        >
          <option value="RTL">右綴じ (RTL)</option>
          <option value="LTR">左綴じ (LTR)</option>
        </select>
      </label>
      <label>
        1ページ目
        <select
          value={options.firstPage}
          onchange={(e) => (options.firstPage = e.currentTarget.value as FirstPage)}
        >
          <option value="right">右始まり</option>
          <option value="left">左始まり (先頭空白)</option>
          <option value="cover">表紙単独</option>
        </select>
      </label>
      <label class="check">
        <input type="checkbox" bind:checked={options.despeckle} /> ノイズ除去 (despeckle)
      </label>
      <label class="check">
        <input type="checkbox" bind:checked={options.register} /> 整列 (register)
      </label>
      <label class="check">
        <input type="checkbox" bind:checked={options.pdfA} /> PDF/A-2b 準拠
      </label>
    </fieldset>

    <button class="primary" disabled={!file || busy} onclick={start}>
      {busy ? "変換中…" : "製本する"}
    </button>
  </section>

  {#if phase === "uploading" || phase === "running" || phase === "done"}
    <section class="card">
      <div class="bar"><div class="fill" style:width={`${percent}%`}></div></div>
      <p class="status">{progress.label}</p>
      {#if phase === "done" && jobId}
        <a class="primary download" href={resultUrl(jobId)}>製本PDFをダウンロード</a>
      {/if}
    </section>
  {/if}

  {#if phase === "failed" && error}
    <section class="card error">
      <strong>変換に失敗しました</strong>
      <p>{error}</p>
    </section>
  {/if}
</main>
