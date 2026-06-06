# tate-yoko-pdf

**縦書き日本語スキャンPDFを見開きレイアウトに変換する CLI ツール**

2つの縦長（ポートレート）ページを1つの横長（ランドスケープ）見開きページに結合し、右綴じ（RTL）の読み順を正しく再現します。どのPDFリーダーでも正しい見開き表示が得られます。

---

## 解決する課題

日本語の縦書き書籍をスキャンしてPDF化すると、各ページは個別の縦長画像として格納されます。一般的なPDFリーダーの見開きモードは横書き（LTR）前提のため、ページの左右が逆転してしまいます。

```
一般的なPDFリーダーの見開き表示（LTR）    tate-yoko-pdf による見開き変換（RTL）

┌─────────┬─────────┐              ┌─────────┬─────────┐
│         │         │              │         │         │
│  Page 1 │  Page 2 │   ──変換──>  │  Page 2 │  Page 1 │
│  (左)   │  (右)   │              │  (左)   │  (右)   │
│         │         │              │         │         │
└─────────┴─────────┘              └─────────┴─────────┘
   ← 読み順が逆！                     → 正しい読み順！
```

---

## 使い方

```bash
./tate-yoko-pdf novel.pdf                       # ゼロ設定でRTL見開き → novel_spread.pdf
./tate-yoko-pdf novel.pdf -o out/spread.pdf     # 出力先指定
./tate-yoko-pdf novel.pdf --first-page left     # 1ページ目を左始まり（先頭ブランク）
./tate-yoko-pdf novel.pdf --first-page cover    # 表紙を単独見開きに
./tate-yoko-pdf textbook.pdf -d LTR             # 横書きPDF用
./tate-yoko-pdf a.pdf b.pdf c.pdf -o out/       # 複数ファイルをまとめて変換（-o はディレクトリ）
./tate-yoko-pdf scans/ -o out/                  # ディレクトリ直下の *.pdf を一括変換
cat in.pdf | ./tate-yoko-pdf - -o - > out.pdf   # stdin → stdout（Unix パイプ連結）
./tate-yoko-pdf novel.pdf -v                    # DEBUGログ
./tate-yoko-pdf --help                          # ヘルプ
```

| オプション | 説明 | デフォルト |
|---|---|---|
| `INPUT...` | 入力PDF。**ファイル（複数可）/ ディレクトリ / `-`（stdin）**（必須） | — |
| `-o`, `--output` | 出力先。単一→ファイル、複数/ディレクトリ入力→出力ディレクトリ、`-`→stdout | `<input>_spread.pdf` |
| `-d`, `--direction` | 読み順: `RTL` または `LTR` | `RTL` |
| `--first-page` | 1ページ目の開始側: `right` / `left` / `cover`（`left` は先頭ブランクで1ページ目を反対側に、`cover` は表紙を単独に） | 読み方向の自然側 |
| `--pdf-a` | PDF/A-2b を出力（保存用・ベストエフォルト） | `false` |
| `--low-memory` | ページストリームを一時ファイルへ退避しヒープを抑制（巨大スキャン向け） | `false` |
| `-v`, `--verbose` | DEBUGレベルのログ出力 | `false` |
| `-h`, `--help` / `--version` | ヘルプ / バージョン表示 | — |

**入力/出力の規則**:
- **複数入力**（複数ファイル列挙 or ディレクトリ）は**バッチ処理**。`-o` を付けると出力ディレクトリ、省略すると各入力の隣に `_spread.pdf` を生成。バッチは1ファイル失敗しても続行し、最後に失敗があれば非0で終了します。
- **`-`** は単独入力時のみ有効（stdin から1つのPDFを読む）。`-o -` で stdout へ出力するとパイプ連結できます。
- **進捗・ログはすべて stderr** に出力されるため、`-o -` の stdout は純粋なPDFバイト列のままです。

### 1ページ目の開き方（`--first-page`）

見開きでは「1ページ目をどちら側から始めるか」で、以降のページの対向（どの2ページが同じ見開きに並ぶか）が変わります。`--first-page` でこれを明示できます（既定は読み方向の自然側＝RTLなら右）。以下はRTLの例で、数字は元PDFのページ番号、`▢` は空白の半分です。

| 値 | 開き | 次 | 説明 |
|---|---|---|---|
| `right`（既定） | `[ 2 \| 1 ]` | `[ 4 \| 3 ]` | 1ページ目を読み始め側（右）に、2ページ目と対で配置（標準） |
| `cover` | `[ ▢ \| 1 ]` | `[ 3 \| 2 ]` | 1ページ目を右に単独配置（表紙扱い）。以降 2·3 / 4·5 |
| `left` | `[ 1 \| ▢ ]` | `[ 3 \| 2 ]` | 先頭に空白を1枚挟み、1ページ目を左に単独配置。以降 2·3 / 4·5 |

- `left` と `cover` は「1ページ目を単独にして以降の対向を1つずらす」点は同じで、**1ページ目が左か右か**だけが異なります。
- LTR（`-d LTR`）では左右が反転します（自然側は左）。

---

## インストール

各OSに **JRE をバンドルした app-image**（zip 1 個・別途 Java インストール不要）を CI で 3 OS 並列にビルドしています。zip を展開して中の launcher を叩くだけで動作します。

| OS | 配布物 | 実行ファイル |
|---|---|---|
| Linux x86_64 | `tate-yoko-pdf-linux.zip` | `bin/tate-yoko-pdf` |
| Windows x86_64 | `tate-yoko-pdf-windows.zip` | `tate-yoko-pdf.exe`（コンソールアプリ） |
| macOS | `tate-yoko-pdf-macos.zip` | `tate-yoko-pdf.app/Contents/MacOS/tate-yoko-pdf` |

最新ビルドは [Actions の最新 run](https://github.com/P4suta/tate-yoko-pdf/actions/workflows/ci.yml) → 任意の成功 run → "Artifacts" から `tate-yoko-pdf-<os>` をダウンロードしてください。

### 既知の制限

- **コード署名なし**: macOS Gatekeeper / Windows SmartScreen で「開発元を確認できません」等の警告が出ます。
  - macOS: 右クリック → 「開く」（初回のみ）、または `xattr -d com.apple.quarantine tate-yoko-pdf.app`
  - Windows: 警告画面で「詳細情報」→「実行」
- **起動時間**: ~500ms（変換処理時間に比べれば微小）。
- **真のインストーラ (.msi/.dmg/.deb) は未対応**: zip 配布のみ。

---

## 開発

開発環境は完全にDocker内で完結します。ホスト側に必要なものは git + Docker + （任意で）mise / lefthook / just のみ。

### 初回セットアップ

```bash
mise install          # lefthook と just を入れる（任意・推奨）
lefthook install      # pre-commit / pre-push hooks を有効化（任意）
docker compose build dev
```

### 日常コマンド

```bash
just                  # 利用可能なレシピ一覧
just check            # test + spotless + errorprone + nullaway + spotbugs + jacoco
just test             # テストのみ
just format           # spotlessApply
just shadow           # shadowJar 生成 (app/build/libs/tate-yoko-pdf-all.jar)
just package          # jpackage app-image を app/build/dist-jpackage/ に生成
just smoke            # app-image をビルドして実 PDF 変換 smoke を回す
just sample-pdf       # app/build/test-data/sample.pdf を生成
just typos            # 誤字スキャン
just typos-fix        # 誤字自動修正
just shell            # devコンテナでシェル
just docker-status    # Docker のディスク占有と本プロジェクトの状態を表示
just docker-clean     # 本プロジェクトの Docker artifacts を一掃
```

`just` を入れていない場合は `docker compose run --rm dev ./gradlew <task>` 形式でも同等。

### 開発支援ツール

| ツール | 役割 |
|---|---|
| Spotless + google-java-format | 全 Java ソースのフォーマット強制 |
| Error Prone | コンパイラ静的解析（ソースレベル、数百のチェック） |
| NullAway (JSpecify mode) | null 安全性検査（`@Nullable` で表現） |
| SpotBugs (MAX effort / MEDIUM confidence) | バイトコードレベル静的解析。`config/spotbugs/exclude.xml` に意図的設計の narrow な suppress を集約 |
| ben-manes versions plugin | 依存ライブラリの更新確認 |
| JaCoCo | テストカバレッジ |
| typos | コメント・識別子の誤字検出（自動修正） |
| lefthook | git pre-commit / pre-push hook（spotless / typos / check） |
| just | タスクランナー |

---

## アーキテクチャ

ヘキサゴナル（Ports & Adapters）を採用し、ドメインロジックを PDF ライブラリから完全に隔離しています。v2.0.0 で各レイヤを **5つの Gradle モジュール**に物理分割し、PDFBox/qpdf の封じ込めやドメイン純粋性は ArchUnit ではなく**クラスパス上に相手が存在しないこと**でコンパイル時に強制されます。

```
:app ──▶ :application ──▶ :port ──▶ :domain（純粋・依存なし）
  └────▶ :infrastructure ──▶ :port, :domain（PDFBox / qpdf はここだけ）
```

例外→終了コードのマッピングと致命的例外ハンドラは、かつての各アプリ `:observability` モジュールに代わり、現在は横断共有の `:shared:observability` + `:shared:cli` が担います。

設計の俯瞰・各モジュールの責務・判断の背景（ADR）は **[docs/architecture.md](docs/architecture.md)** と **[docs/adr/](docs/adr/)** を参照してください。

---

## 技術スタック

| カテゴリ | 技術 | バージョン |
|---|---|---|
| 言語 | Java | 25 |
| PDF操作 | Apache PDFBox | 3.0.7 |
| CLI | Apache Commons CLI | 1.11.0 |
| ロギング | SLF4J + slf4j-simple | 2.0.18 |
| ビルド | Gradle (Kotlin DSL) | 9.5.1 |
| Fat JAR | Shadow Plugin | 9.4.2 |
| 配布 | jlink + jpackage (Liberica JDK Full 25 同梱) | OpenJDK 25 |
| テスト | JUnit Jupiter / AssertJ / jqwik / ArchUnit | 6.1.0 / 3.27.7 / 1.10.1 / 1.4.2 |
| 静的解析 | Error Prone / NullAway / JSpecify / SpotBugs | 2.49.0 / 0.13.4 / 1.0.0 / 6.5.5 |
| フォーマット | Spotless + google-java-format | 8.6.0 / 1.35.0 |
| カバレッジ | JaCoCo | 0.8.13 |

---

## テスト

```bash
just check    # 全テスト + 静的解析 + カバレッジ (JaCoCo 層別 threshold)
just test     # テストのみ
```

テストは多層構成:
- **Unit** (`domain.*`, `application`, `domain.exception`) — 純粋ロジック、外部依存なし
- **Property-based** (`jqwik`) — Pagination / SpreadLayoutCalculator の不変条件を多数ケースで検証
- **Integration** (`infrastructure.pdfbox` / `infrastructure.qpdf`) — 実 PDFBox / qpdf 経由で破損・暗号化・回転 PDF や linearize を扱う
- **Architecture** (`architecture`、`:app`) — ArchUnit で残る2点（`domain.strategy` の直接生成禁止・パッケージ循環禁止）を強制。他の境界はモジュール分割によりコンパイル時に強制される
- **CLI** (`cli`) — `SpreadCommand.run` を直接呼び stdout/stderr/exit code を assert（バッチ・stdin/stdout 含む）

JaCoCo はモジュール別 threshold で `check` の必須ゲート: `:domain` 95%/90% / `:application` 85%/65% / `:infrastructure` 75%/60%（行/分岐）。

---

## トラブルシュート

### エラーが出たら

CLI は `Error[KIND]: ...` を **stderr** に出力します。**ErrorKind** で原因が判別できます。

| ErrorKind | 意味 | 対処 |
|---|---|---|
| `PDF_CORRUPTED` | PDF が破損している | 別ツールで開けるか確認、再エクスポート |
| `PDF_PASSWORD_PROTECTED` | パスワード保護されている | 保護を解除した PDF を渡す |
| `PDF_NOT_FOUND` | 指定したファイルが存在しない | パスを確認 |
| `PDF_INVALID_PAGE` | ページ数 ≤ 0 など不正なページ指定 | 入力 PDF を確認 |
| `PDF_WRITE_FAILED` | 出力先に書き込めない | 書き込み権限 / 空き容量を確認 |
| `INVALID_PARAMETER` | `direction` が `RTL`/`LTR` 以外、など | パラメータを確認 |
| `OUT_OF_MEMORY` | JVM ヒープ不足 | `JAVA_TOOL_OPTIONS=-Xmx1g` でヒープを増やす |
| `INTERNAL` | 上記以外の予期しないエラー | `-v` で詳細を取得 |

### CLI exit code (sysexits.h 風)

| code | 意味 |
|---:|---|
| 0 | 成功 |
| 1 | バッチで一部のファイルが失敗 |
| 2 | コマンドライン使い方エラー（不正なオプション / 方向） |
| 64 | パラメータが不正 |
| 65 | PDF が破損 / 不正 |
| 66 | 入力ファイルが存在しない |
| 70 | 予期しない内部エラー |
| 73 | 出力に書き込めない |
| 77 | パスワード保護 PDF |
| 137 | メモリ不足 (OutOfMemory = 128 + SIGKILL) |

シェルから連結する例: `tate-yoko-pdf in.pdf -o out.pdf || echo "failed: exit=$?"`。

### 詳細ログ

- `-v` / `--verbose` で DEBUG ログ + stack trace + technicalDetail。
- ログは**すべて stderr** に出力されます（stdout は `-o -` の PDF バイト専用）。ファイルへのログ出力はありません。

### qpdf による後処理（linearize + PDF 1.7）

生成 PDF は **qpdf** で linearize（Fast Web View bytes-order）し、ヘッダを PDF 1.7 に揃えます（PDFBox は catalog `/Version` しか更新できないため）。

- **Linux x86_64 / Windows x86_64** app-image: qpdf バイナリを upstream zip から自動同梱（追加インストール不要）。
- **macOS** および dev 実行: PATH 上の qpdf を使用します（`brew install qpdf` / `apt-get install qpdf`）。未インストール時は valid だが非 linearized な PDF を出力します（catalog `/Version` は 1.7、その他の挙動は同等）。
- バイナリは Apache License 2.0（[qpdf license](https://github.com/qpdf/qpdf/blob/main/Artistic-2.0)）。

### 配布 (bundled JRE)

PDFBox は内部で `java.awt.image.Raster` / `ColorModel` を経由してフォントとカラーマネジメントを扱うため AWT が必須です。GraalVM native-image は AWT の macOS/Windows 対応が不安定だったため、**jlink + jpackage で JRE を bundle した app-image** で配布します。

- **ビルドフロー**: `just package` → `shadowJar` で fat JAR → `jlink` で必要モジュールだけのトリム JRE → `jpackage --type app-image` で launcher + JRE + JAR + qpdf を 1 ディレクトリに同梱。Windows は `--win-console` でコンソールアプリとしてビルドします。
- **dev container 要件**: Debian + **Liberica JDK Full Edition 25**（`jmods/` を含むので jlink が走る）+ binutils（`jlink --strip-debug` が `objcopy` を呼ぶ）+ qpdf。
- **OS 別**: jpackage はクロスビルド非対応のため、各 OS 用 app-image は CI で各 OS のランナー上で同じ Gradle タスクを実行して作ります。

---

## ライセンス

[MIT License](LICENSE)

---

## 著者

**P4suta** — [GitHub](https://github.com/P4suta)
