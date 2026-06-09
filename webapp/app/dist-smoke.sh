#!/usr/bin/env bash
# End-to-end smoke for the self-contained webapp app-image (Linux + macOS). Start the server
# PATH-EMPTY so the launcher's baked -Dp4suta.pdfbook.path must resolve the NESTED pdfbook, convert a
# real scan through it, and assert a linearized PDF — proving the whole chain (bundled-JRE server +
# nested pdfbook + its bundled natives + qpdf) works with no Docker, no JDK, no PATH tools. $1 = the
# per-OS app-image launcher. Used by the dist-linux webapp step and the dist-webapp-crossos macOS leg;
# the Windows leg uses dist-smoke.ps1 (same contract).
set -uo pipefail
launcher="${1:?usage: dist-smoke.sh <launcher>}"
sample="register/infrastructure/build/sample/sample.pdf"
base="http://127.0.0.1:8080"

[ -x "$launcher" ] || { echo "::error::launcher not found/executable: $launcher"; exit 1; }
[ -s "$sample" ] || { echo "::error::sample PDF not found: $sample"; exit 1; }

# Background the server PATH-empty (do NOT add pdfbook to PATH — the bundled -D must resolve it).
"$launcher" > server.log 2>&1 &
server=$!
result="$(mktemp)"
cleanup() { kill "$server" 2>/dev/null || true; rm -f "$result"; }
trap cleanup EXIT

fail() { echo "::error::$1"; echo "--- server.log (tail) ---"; tail -50 server.log; exit 1; }

# 1) health UP — proves the context started and the nested pdfbook binary resolved.
up=
for _ in $(seq 1 45); do
  if curl -fsS "$base/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then up=1; break; fi
  sleep 2
done
if [ "$up" != 1 ]; then
  # The default profile shows full health details — curl WITHOUT -f to capture the 503/DOWN body
  # (which indicator failed + its path), then fail.
  echo "--- /actuator/health (last response, may be 503/DOWN) ---"
  curl -s "$base/actuator/health" 2>/dev/null
  echo
  fail "server did not become healthy"
fi
echo "[smoke] health UP"

# 2) submit the scan — explicit PDF content-type (a bare -F sends octet-stream, which the controller
# rejects). curl is on every runner (incl. Windows: curl.exe).
resp=$(curl -fsS -X POST "$base/api/v1/jobs" -F "file=@${sample};type=application/pdf")
id=$(printf '%s' "$resp" | sed -nE 's/.*"jobId"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')
[ -n "$id" ] || fail "no job id in response: $resp"
echo "[smoke] submitted job $id"

# 3) poll status until DONE — the SSE runCompleted event precedes DONE / a ready /result, so status is
# the only reliable readiness signal. Break early on FAILED (surface the server-side error).
state=
for _ in $(seq 1 90); do
  s=$(curl -fsS "$base/api/v1/jobs/$id")
  state=$(printf '%s' "$s" | sed -nE 's/.*"state"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')
  [ "$state" = DONE ] && break
  [ "$state" = FAILED ] && fail "conversion FAILED: $s"
  sleep 2
done
[ "$state" = DONE ] || fail "conversion did not reach DONE (last state: $state)"
echo "[smoke] job DONE"

# 4) download the result and assert %PDF + Linearized (the nested pdfbook + qpdf both ran).
curl -fsS "$base/api/v1/jobs/$id/result" -o "$result"
test -s "$result" || fail "no result PDF downloaded"
head -c5 "$result" | grep -q '%PDF-' || fail "result is not a PDF"
grep -aq Linearized "$result" || fail "qpdf linearize did not run (broken nest or native)"
echo "[smoke] OK: health UP -> DONE -> %PDF + Linearized ($(wc -c < "$result") bytes)"
