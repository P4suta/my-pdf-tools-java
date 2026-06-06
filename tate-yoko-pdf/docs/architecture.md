# アーキテクチャ

`tate-yoko-pdf` の設計の俯瞰と判断（ADR）の索引。**使い方・CLI オプションは
[README](../README.md) が一次情報源**で、本書は重複させない。本書が扱うのは「なぜこの構造なのか」。

## 全体像 — ヘキサゴナル(Ports & Adapters)

ドメイン(見開きレイアウトの幾何とページネーション)を中心に置き、PDF エンジンや CLI といった
外界の関心事をポート(インターフェース)の外側へ追い出す。レイアウトのコアロジックは PDFBox や
qpdf、Apache Commons CLI を一切知らない。

レイヤ境界は **Gradle のマルチモジュール**で物理化されている。依存方向は ArchUnit の規約ではなく
**クラスパス上に相手が存在しないこと**で強制される(詳細は [ADR-0008](adr/0008-gradle-multi-module-split.md))。

```
                 ┌─────────────────────────────────────────┐
   :app  ───────▶│ :application ──▶ :port ──▶ :domain        │
  (cli +         │      │                        ▲           │
   Main +        │      └────────────────────────┘           │
   配布)          │ :infrastructure ──▶ :port, :domain         │
     │           └─────────────────────────────────────────┘
     └──────────▶ 依存先: application / infrastructure / port / domain
```

| モジュール | 役割 | 依存 | 外部ライブラリ |
|---|---|---|---|
| `:domain` | 純粋なコア。model(record/enum/sealed) ・幾何計算・ページネーション戦略・例外語彙 | なし | なし |
| `:port` | ヘキサゴンのポート(インターフェース) | `:domain` | なし |
| `:application` | ユースケース調整(`SpreadService`)・オプション・戦略選択 | `:domain`, `:port` | SLF4J/Logback |
| `:infrastructure` | PDFBox / qpdf アダプタ | `:domain`, `:port` | **PDFBox・xmpbox・qpdf はここだけ** |
| `:app` | CLI(Commons CLI)・`Main`・shadow・jpackage/jlink・qpdf staging・dev ツール | 上記すべて | Commons CLI・Logback・(test)veraPDF |

例外→終了コード/ログ変換・PII マスクは、横断共有の `:shared:observability` + `:shared:cli` が担う。

共有ビルド設定は `build-logic/` の included build にある3つの規約プラグイン
(`java-conventions` / `test-conventions` / `quality-conventions`)に集約し、各モジュールはそれを
適用するだけにしている。

### 境界の強制

- **PDFBox 封じ込め・ドメイン純粋性・ポート純粋性**: モジュール分割によりクラスパス上に相手が
  無いため、コンパイル時点で違反不能。
- **残る ArchUnit ルール**(`:app` の `LayerDependencyTest`): モジュールグラフでは防げない2点のみ
  — 「`domain.strategy` を直接 new するのはアプリ層だけ」と「パッケージ循環の禁止」。
- **品質ゲート**(全モジュール共通、`build-logic` 由来): Spotless(google-java-format)・Error Prone +
  NullAway(ERROR)・SpotBugs(MAX)・JaCoCo(ドメイン 0.95/0.90, application 0.85/0.65,
  infrastructure 0.75/0.60)・Javadoc doclint・pitest(domain/application)。

## Javadoc 規約

公開境界(ポート・アプリ公開 API・ドメイン公開型)は次を記す: ①責務(ドメイン語彙で) ②`@param`
(index は 0 始まり等の規約も) ③`@return`(所有権/ライフタイム) ④nullability は JSpecify `@Nullable`
に委ね非自明時のみ散文 ⑤`@throws SpreadException` と発生し得る `ErrorKind` ⑥スレッド/ライフサイクル
(`SourceDocument`/`SpreadDocument` の open→use→close、`applyMetadata`→`finalizePdfA` 順序、
`ProgressListener` の呼び出し順)。`Xdoclint:all,-missing` を `check` に接続し、壊れた `@link` 等を
ビルドで弾く。

## バージョニング方針

[SemVer](https://semver.org/lang/ja/) に従う。**CLI の破壊的変更(オプション削除・デフォルト変更・
出力フォーマット変更)はメジャーを上げる**。`SpreadCommand.VERSION`(`--version` の出力)が権威ある
バージョン文字列。例: 非推奨 `--cover-single` の削除は v2.0.0 で実施した([ADR-0006](adr/0006-commons-cli-over-picocli.md) 関連)。

## ADR 索引

設計判断は [Architecture Decision Record](adr/) として記録する。新規は
[テンプレート](adr/0000-template.md) を複製して採番。

| # | タイトル | ステータス |
|---|---|---|
| [0001](adr/0001-hexagonal-ports-and-adapters.md) | ヘキサゴナル Ports & Adapters の採用 | 採用 |
| [0002](adr/0002-qpdf-out-of-process-post-processor.md) | qpdf をアウトプロセス後処理として採用 | 採用 |
| [0003](adr/0003-pdfbox-as-pdf-engine.md) | PDF エンジンに PDFBox を採用 | 採用 |
| [0004](adr/0004-pagination-strategy-pattern.md) | ページネーションを Strategy パターンで表現 | 採用 |
| [0005](adr/0005-bundled-trimmed-jre-jlink-jpackage.md) | jlink + jpackage でトリム JRE を同梱 | 採用 |
| [0006](adr/0006-commons-cli-over-picocli.md) | Picocli から Apache Commons CLI へ移行 | 採用 |
| [0007](adr/0007-pdf-a-2b-best-effort-approach.md) | PDF/A-2b をベストエフォートで出力 | 採用 |
| [0008](adr/0008-gradle-multi-module-split.md) | Gradle マルチモジュール分割 | 採用 |
