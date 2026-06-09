# End-to-end smoke for the self-contained webapp app-image (Windows). Start the server PATH-EMPTY so
# the launcher's baked -Dp4suta.pdfbook.path must resolve the NESTED pdfbook, convert a real scan, and
# assert a linearized PDF. -Launcher = the app-image .exe. Mirror of dist-smoke.sh (same contract).
param([Parameter(Mandatory = $true)][string]$Launcher)
$ErrorActionPreference = "Stop"
$sample = "register\infrastructure\build\sample\sample.pdf"
$base = "http://127.0.0.1:8080"
if (-not (Test-Path $Launcher)) { throw "launcher not found: $Launcher" }
if (-not (Test-Path $sample)) { throw "sample PDF not found: $sample" }

# Background the server PATH-empty; the --win-console launcher streams to the redirected files.
$proc = Start-Process -FilePath $Launcher -PassThru -NoNewWindow `
    -RedirectStandardOutput server.out.log -RedirectStandardError server.err.log
$result = New-TemporaryFile

function Stop-Server {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    if ($result) { Remove-Item $result -Force -ErrorAction SilentlyContinue }
}
function Fail($msg) {
    Write-Host "::error::$msg"
    foreach ($f in "server.out.log", "server.err.log") {
        if (Test-Path $f) { Write-Host "--- $f (tail) ---"; Get-Content $f -Tail 50 }
    }
    Stop-Server
    exit 1
}

try {
    # 1) health UP — proves the context started and the nested pdfbook binary resolved.
    $up = $false
    foreach ($i in 1..45) {
        try {
            $h = (Invoke-WebRequest -UseBasicParsing "$base/actuator/health").Content | ConvertFrom-Json
            if ($h.status -eq "UP") { $up = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $up) { Fail "server did not become healthy" }
    Write-Host "[smoke] health UP"

    # 2) submit — curl.exe with explicit PDF content-type (a bare -F sends octet-stream, rejected).
    $resp = (& curl.exe -sS -X POST "$base/api/v1/jobs" -F "file=@$sample;type=application/pdf") | Out-String
    if ($resp -notmatch '"jobId"\s*:\s*"([^"]+)"') { Fail "no job id in response: $resp" }
    $id = $Matches[1]
    Write-Host "[smoke] submitted job $id"

    # 3) poll status until DONE — status is the only reliable readiness signal (the SSE runCompleted
    # event precedes DONE / a ready /result). Break early on FAILED.
    $done = $false
    foreach ($i in 1..90) {
        $s = (Invoke-WebRequest -UseBasicParsing "$base/api/v1/jobs/$id").Content | ConvertFrom-Json
        if ($s.state -eq "DONE") { $done = $true; break }
        if ($s.state -eq "FAILED") { Fail "conversion FAILED: $($s.errorKind) $($s.errorMessage)" }
        Start-Sleep -Seconds 2
    }
    if (-not $done) { Fail "conversion did not reach DONE" }
    Write-Host "[smoke] job DONE"

    # 4) download + assert %PDF + Linearized (the nested pdfbook + qpdf both ran).
    & curl.exe -sS "$base/api/v1/jobs/$id/result" -o $result.FullName
    if ((Get-Item $result.FullName).Length -eq 0) { Fail "no result PDF downloaded" }
    $bytes = [System.IO.File]::ReadAllBytes($result.FullName)
    $text = [System.Text.Encoding]::Latin1.GetString($bytes)
    if (-not $text.StartsWith("%PDF-")) { Fail "result is not a PDF" }
    if (-not $text.Contains("Linearized")) { Fail "qpdf linearize did not run (broken nest or native)" }
    Write-Host "[smoke] OK: health UP -> DONE -> %PDF + Linearized ($($bytes.Length) bytes)"
} finally {
    Stop-Server
}
