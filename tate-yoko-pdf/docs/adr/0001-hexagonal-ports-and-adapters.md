# ADR-0001: ヘキサゴナル Ports & Adapters の採用

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0003](0003-pdfbox-as-pdf-engine.md)（PDFBox 封じ込め）, [ADR-0008](0008-gradle-multi-module-split.md)（境界の物理化）

## コンテキスト

本ツールの本質は「2ページを1枚の見開きに配置する幾何計算とページ対の生成」であり、PDF の読み書き
そのものではない。コアのレイアウトロジックを特定の PDF ライブラリの API に直結させると、ライブラリ
更新や差し替えのたびにロジックが巻き込まれ、テストも実 PDF への依存で重くなる。

## 決定

ヘキサゴナル(Ports & Adapters)を採用する。ドメイン(model・幾何計算・ページネーション戦略)を中心に
置き、外界(PDF I/O、後処理、CLI)はポート(`DocumentFactory`, `SourceDocument`, `SpreadDocument`,
`PageContent`, `PagePlacement`, `PdfPostProcessor`)越しにのみアクセスする。ポートはドメインの語彙
(`PageDimension`, `LayoutPosition` 等)で会話する。

## 根拠

- ドメインは純粋な値計算となり、`SpreadLayoutCalculator` 等を property-based test(jqwik)で網羅的に
  検証できる。
- アダプタ(PDFBox/qpdf)はモック可能で、`SpreadService` のオーケストレーションをドメインから独立に
  テストできる。
- PDF エンジンの差し替えがドメインに波及しない。

## 検討した代替案

- **PDFBox 直叩きの素朴な層構成**: 初期速度は速いが、テスト容易性とコア保護を失う。本プロジェクトが
  避けたかった「動けばよい」の典型。
- **汎用 DI フレームワーク導入**: コンストラクタ注入と CLI 内の小さな合成ルートで十分で、依存追加は
  過剰。

## 影響

- `port/` をポート、`infrastructure/` をアダプタとする構成。当初は ArchUnit(`LayerDependencyTest`)で
  依存方向を強制していた。
- v2.0.0 でこの境界を Gradle モジュールへ物理化し、PDFBox 封じ込め等はクラスパスでコンパイル時強制に
  なった([ADR-0008](0008-gradle-multi-module-split.md))。
