# pdfbook

The unified pipeline: convert self-scanned Japanese-book PDFs end-to-end in one
pass — extract the scan's pages once, remove scanner dust (despeckle), straighten
and align them onto a fixed canvas (register), then combine into right-to-left
two-page spreads. No intermediate PDFs: every stage works over a shared image
working-set in a per-run temp area, and the spread is the only repack.

The three feature tools (`register`, `despeckle`, `tate-yoko-pdf`) supply their
application services as pipeline stages; this module wires them through a
`Source → Stage* → Sink` runner.

## Usage

```
pdfbook [options] <in.pdf>... | <in-dir> -o <out.pdf|out-dir>
```

One input writes to the `-o` file; a directory (or several PDFs) batches every
top-level `*.pdf` into the `-o` directory, continue-on-error (one failed book
never stops the rest; existing outputs are skipped unless `--force`).

| option | default | meaning |
| --- | --- | --- |
| `-o, --output <path>` | (required) | output PDF (single input) or directory (batch) |
| `-d, --direction <RTL\|LTR>` | `RTL` | reading direction of the spreads |
| `--first-page <right\|left\|cover>` | `right` | which side page one opens on (`left` = leading blank, `cover` = page one alone) |
| `--no-despeckle` | off | skip the dust-removal stage |
| `--no-register` | off | skip the deskew/alignment stage |
| `--no-deskew` | off | in register, do not straighten each page |
| `--no-scale` | off | in register, do not scale columns to the reference height |
| `-j, --jobs <n>` | CPU cores | worker threads per book |
| `--pdf-a` | off | emit PDF/A-2b conformance |
| `--force` | off | overwrite an existing output (batch: regenerate, don't skip) |
| `--progress-file <path>` | — | write machine-readable JSONL progress events (single input only) |
| `--timings` | off | print a per-stage wall-clock breakdown to stderr when each run ends |
| `-i, --interactive` | off | guided mode: prompt for the input, options and output |
| `-h, --help` | — | show help and exit |
| `-V, --version` | — | print version and exit |
| `-v, --verbose` | off | enable verbose (DEBUG) logging |
| `--completion <bash\|zsh\|fish>` | — | print a shell completion script and exit |
| `--man` | — | print a man page (troff) and exit |

A bare `pdfbook` prints help and exits 0. The same `--completion` / `--man` flags
are available on every tool in this repo.

### Interactive mode

`pdfbook -i` walks you through the conversion: pick the input PDF, choose reading
direction / first-page / which stages to run (defaults shown in brackets), name
the output, confirm an overwrite, then watch a live progress bar. It needs a
terminal — a piped or non-TTY `-i` fails fast rather than blocking — so scripts
should use the flag form above.

```sh
pdfbook -i
```

```sh
pdfbook --completion bash > /etc/bash_completion.d/pdfbook   # tab-complete options
pdfbook --man > /usr/share/man/man1/pdfbook.1                # install the man page
```

```sh
pdfbook scan.pdf -o book.pdf                  # one book
pdfbook scans/ -o out/                         # batch a directory
pdfbook scan.pdf -o book.pdf --no-despeckle    # skip dust removal
```

## Build & run

Built and run only inside the dev container (no host JDK):

```sh
just pdfbook-install                 # build pipeline/app/build/install/pdfbook/bin/pdfbook
just pdfbook scan.pdf -o book.pdf    # build (if needed) and run
```

`pdfbook` shells out to `pdfimages` / `pdfinfo` / `jbig2` / `qpdf`; the dev image
bundles them.

## Exit codes

CLI exit codes follow [the shared error model](../shared/observability/) (sysexits):
`0` success, `2` usage/parse error, `64` bad value, `65` unreadable image, `66`
input not found, `70` internal / native-tool failure, `73` output exists (pass
`--force`), `74` I/O error, `137` out of memory. Errors print as `Error[KIND]: …`;
the `KIND` token is the stable error vocabulary shared with the web UI.
