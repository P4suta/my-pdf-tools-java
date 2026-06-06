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
  | { type: "runFailed"; kind: string; message: string };

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
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message ?? `submit failed (HTTP ${response.status})`);
  }
  const accepted = (await response.json()) as { jobId: string };
  return accepted.jobId;
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

export function resultUrl(jobId: string): string {
  return `/api/v1/jobs/${jobId}/result`;
}
