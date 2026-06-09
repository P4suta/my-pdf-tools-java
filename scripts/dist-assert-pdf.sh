#!/usr/bin/env bash
# Assert a produced PDF: the file exists and is non-empty, starts with the %PDF- magic, and contains
# each given marker token (e.g. Linearized, JBIG2Decode). Shared by the per-OS CLI app-image smokes
# (pdfbook / register / despeckle in distribution.yml) so the same assertion is not re-spelled per OS;
# the launcher invocation stays in the workflow (its arguments differ per app). Mirror of
# dist-assert-pdf.ps1.
#
# Usage: dist-assert-pdf.sh <pdf> [marker-token ...]
set -euo pipefail

pdf="${1:?usage: dist-assert-pdf.sh <pdf> [marker-token ...]}"
shift

test -s "$pdf" || {
    echo "::error::no output PDF produced: $pdf"
    exit 1
}
head -c5 "$pdf" | grep -q '%PDF-' || {
    echo "::error::$pdf is not a PDF (no %PDF- magic)"
    exit 1
}
for token in "$@"; do
    grep -aq "$token" "$pdf" || {
        echo "::error::$pdf is missing the expected marker: $token"
        exit 1
    }
done

echo "smoke OK: $pdf — %PDF${*:+ + $*}"
