#!/usr/bin/env pwsh
# Assert a produced PDF: the file exists and is non-empty, starts with the %PDF- magic, and contains
# each given marker token (e.g. Linearized, JBIG2Decode). Shared by the per-OS CLI app-image smokes
# (pdfbook / register / despeckle in distribution.yml) so the same assertion is not re-spelled per OS;
# the launcher invocation stays in the workflow (its arguments differ per app). Mirror of
# dist-assert-pdf.sh.
#
# Usage: dist-assert-pdf.ps1 <pdf> [marker-token ...]
param(
    [Parameter(Mandatory)][string]$Pdf,
    [Parameter(ValueFromRemainingArguments)][string[]]$Tokens
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path $Pdf) -or (Get-Item $Pdf).Length -eq 0) {
    throw "no output PDF produced: $Pdf"
}
# Latin1 so the byte stream maps 1:1 to chars (markers are ASCII); matches dist-smoke.ps1.
$text = [System.Text.Encoding]::Latin1.GetString([System.IO.File]::ReadAllBytes($Pdf))
if (-not $text.StartsWith("%PDF-")) {
    throw "$Pdf is not a PDF (no %PDF- magic)"
}
foreach ($token in $Tokens) {
    if (-not $text.Contains($token)) {
        throw "$Pdf is missing the expected marker: $token"
    }
}

Write-Host "smoke OK: $Pdf — %PDF $($Tokens -join ' ')"
