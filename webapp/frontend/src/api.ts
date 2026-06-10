// Thin client for the pdfbook web API. The progress stream is SSE: the server sends each
// ProgressEvent as a default "message" whose data is the shared JSONL line.

export type Direction = "RTL" | "LTR";
export type FirstPage = "right" | "left" | "cover";

export interface ConversionOptions {
  direction: Direction;
  firstPage: FirstPage;
  despeckle: boolean;
  register: boolean;
  deskew: boolean;
  scale: boolean;
  pdfA: boolean;
}

export type ProgressEvent =
  | { type: "runStarted"; stageCount: number }
  | { type: "stageStarted"; stage: string; index: number; stageCount: number }
  | { type: "pageProcessed"; stage: string; done: number; total: number }
  | { type: "stageCompleted"; stage: string }
  | { type: "runCompleted" }
  // The server blanks `message` at the HTTP boundary (it holds server-only diagnostics); the UI
  // localizes from the stable `kind` alone, so `message` is intentionally not part of the type.
  | { type: "runFailed"; kind: string };

export async function submitJob(file: File, options: ConversionOptions): Promise<string> {
  const form = new FormData();
  form.append("file", file);
  form.append("direction", options.direction);
  form.append("firstPage", options.firstPage);
  form.append("despeckle", String(options.despeckle));
  form.append("register", String(options.register));
  form.append("deskew", String(options.deskew));
  form.append("scale", String(options.scale));
  form.append("pdfA", String(options.pdfA));

  const response = await fetch("/api/v1/jobs", { method: "POST", body: form });
  if (!response.ok) {
    throw new Error(await problem(response, "submit failed"));
  }
  const accepted = (await response.json()) as { jobId: string };
  return accepted.jobId;
}

// Hash the file locally so the server can tell us whether this exact conversion is already cached —
// letting us skip uploading a (potentially large) PDF. crypto.subtle requires a secure context
// (HTTPS or localhost); over plain HTTP it is undefined, so we return null and fall back to a normal
// upload.
export async function sha256Hex(file: File): Promise<string | null> {
  if (!globalThis.crypto?.subtle) {
    return null;
  }
  try {
    const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  } catch {
    return null; // could not read/hash the file; just upload it normally
  }
}

// Ask the server whether the result for these exact bytes + options is already cached. A 200 returns
// a ready job id (so the upload can be skipped); a 204 means "not cached — upload it".
export async function probe(
  sha256: string,
  options: ConversionOptions,
  originalFilename: string,
): Promise<string | null> {
  const response = await fetch("/api/v1/jobs/probe", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sha256, ...options, originalFilename }),
  });
  if (response.status === 204) {
    return null;
  }
  if (!response.ok) {
    throw new Error(await problem(response, "probe failed"));
  }
  const accepted = (await response.json()) as { jobId: string };
  return accepted.jobId;
}

export type JobState = "QUEUED" | "RUNNING" | "DONE" | "FAILED";

export interface JobStatus {
  state: JobState;
  // Only the stable kind crosses the boundary; the failure detail stays server-side (log + Job
  // record). The UI localizes from `errorKind`.
  errorKind: string | null;
}

// The authoritative job state. The `runCompleted` progress event means pdfbook finished writing,
// but the result is only downloadable once the server has reaped the subprocess and flipped the
// job to DONE — so the UI confirms readiness here rather than trusting the progress stream.
export async function getStatus(jobId: string): Promise<JobStatus> {
  const response = await fetch(`/api/v1/jobs/${jobId}`);
  if (!response.ok) {
    throw new Error(await problem(response, "status failed"));
  }
  return (await response.json()) as JobStatus;
}

export interface RuntimeInfo {
  // Whether this is the auto-shutdown desktop build (false for the reverse-proxied prod server).
  autoShutdown: boolean;
}

export async function fetchRuntime(): Promise<RuntimeInfo> {
  const response = await fetch("/api/v1/runtime");
  if (!response.ok) {
    throw new Error(await problem(response, "runtime info failed"));
  }
  return (await response.json()) as RuntimeInfo;
}

// Hold one SSE connection open for the page's lifetime; its mere existence tells the desktop
// app-image the browser is still here. Closing the tab drops the connection (TCP FIN) and the
// server shuts itself down after a short grace — no polling on either side. Opened only when
// fetchRuntime() reports autoShutdown, so the prod server is never connected. We never read from
// it (the server may send keep-alive comments); EventSource auto-reconnects on its own.
export function openPresence(): EventSource {
  return new EventSource("/api/v1/presence");
}

export function streamProgress(
  jobId: string,
  onEvent: (event: ProgressEvent) => void,
  onConnectionError: () => void,
): EventSource {
  const source = new EventSource(`/api/v1/jobs/${jobId}/events`);
  source.onmessage = (message) => {
    onEvent(JSON.parse(message.data) as ProgressEvent);
  };
  source.onerror = () => {
    // EventSource auto-reconnects; the server replays the buffer on reconnect, so a blip is benign.
    // We only surface it if the stream never delivered a terminal event (handled by the caller).
    onConnectionError();
  };
  return source;
}

// The result URL ends in the desired download filename: a browser previewing an `inline` PDF names
// the download from the URL's last path segment, so `.../result/<name>_book.pdf` downloads as that
// rather than a generic "result.pdf". The server ignores the trailing segment (the job id alone
// authorizes); it is purely there to name the file.
export function resultUrl(jobId: string, filename?: string): string {
  const base = `/api/v1/jobs/${jobId}/result`;
  return filename ? `${base}/${encodeURIComponent(filename)}` : base;
}

// The API reports failures as RFC 9457 problem+json: a human-readable `detail` plus a stable machine
// `code`. Pull the `detail` for display, falling back to the HTTP status when the body is missing or
// unparsable.
async function problem(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { detail?: string } | null;
  return body?.detail ?? `${fallback} (HTTP ${response.status})`;
}
