# ADR-0008: Gradle マルチモジュール分割

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0001](0001-hexagonal-ports-and-adapters.md)

## コンテキスト

ヘキサゴナルのレイヤ境界は当初 ArchUnit(`LayerDependencyTest`)で守っていた。これは「規約による強制」
であり、ルールを消せば違反が通る。境界をより強固に、ビルドそのもので保証したかった。

## 決定

単一モジュールを **6つの Gradle モジュール**に分割する: `:domain` / `:port` / `:application` /
`:infrastructure` / `:observability` / `:app`。共有ビルド設定は `build-logic/` の included build にある
規約プラグイン3種(java / test / quality)へ集約する。

## 根拠

- PDFBox/qpdf 封じ込め・ドメイン純粋性・ポート純粋性・「application は cli に依存しない」は、相手が
  **クラスパス上に存在しない**ことでコンパイル時に強制される(ArchUnit 規約より強い)。
- 規約プラグインで toolchain・Error Prone+NullAway・Spotless・SpotBugs・JaCoCo・doclint を一度だけ定義し、
  各モジュールは適用するだけにできる。
- モジュール単位でカバレッジ床(domain 0.95/0.90 等)を課せる。

## 検討した代替案

- **単一モジュール + ArchUnit 継続**: 約1000行と小規模なので過剰分割の懸念はあった。しかし境界の物理
  強制という価値が、6モジュールのビルドファイル増加コストを上回ると判断した。
- **`subprojects {}` での横断設定**: configuration cache(本プロジェクトは有効)と相性が悪く、included
  build の規約プラグインを選んだ。

## 影響

- 残る ArchUnit ルールは2点のみ(`domain.strategy` 直接 new の禁止・パッケージ循環の禁止)。他5ルールは
  コンパイル強制となり削除。
- testFixtures を分割(`PdfFixtures`→`:infrastructure`, `CapturingProgressListener`→`:application`)。
  end-to-end の `PdfAConformanceTest` と `LayerDependencyTest` は全モジュールを見られる `:app` に置く。
- 配布物・テストレポートのパスが `app/build/`・`*/build/` に移り、`checkExtraVersions` の正規表現・CI・
  `justfile`・`bench-runtime` をそれに合わせて更新した。
- モジュール分離により、横断テストが寄与していたカバレッジを各モジュールが自前テストで賄う必要が生じ、
  ドメイン値型(`DocumentMetadata` 等)に単体テストを追加した。
