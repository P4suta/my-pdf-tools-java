# ADR-0003: PDF エンジンに PDFBox を採用

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0001](0001-hexagonal-ports-and-adapters.md), [ADR-0005](0005-bundled-trimmed-jre-jlink-jpackage.md)

## コンテキスト

見開き化は、元 PDF の各ページを **FormXObject として新しいページに埋め込み**、平行移動して配置する
操作が中核となる。加えてページ寸法・回転の取得、メタデータの引き継ぎ、PDF/A 用の XMP/出力インテント
生成が必要。これらを担う JVM 上の PDF ライブラリを選ぶ。

## 決定

Apache PDFBox 3.0.7 を採用する。ページの form 化(`LayerUtility.importPageAsForm`)、`PDPageContentStream`
での変換描画、`PDDocumentInformation` でのメタデータ、付属の xmpbox での PDF/A XMP 生成を用いる。
PDFBox 依存は `:infrastructure` モジュールに封じ込める。

## 根拠

- FormXObject 埋め込み・低レベルなコンテンツストリーム操作を素直に行える成熟ライブラリ。
- xmpbox が同梱され、PDF/A の XMP パケット生成を追加依存なしで賄える。
- Apache ライセンスで OSS と親和的。

## 検討した代替案

- **iText**: 機能は十分だが AGPL/商用ライセンスで OSS 配布に制約。
- **OpenPDF**: より軽量だが、必要な低レベル操作と PDF/A サポートの成熟度で PDFBox に劣る。

## 影響

- PDFBox は AWT(`java.desktop`)に依存する(`PDDocument` の clinit が `java.awt.image.Raster` 等に触れる)。
  これが GraalVM native-image を避け、jlink/jpackage で `java.desktop` を含むトリム JRE を同梱する
  方針([ADR-0005](0005-bundled-trimmed-jre-jlink-jpackage.md))の主要因となった。
- PDFBox はヘッダバイトの版を更新しないため、qpdf 後処理が必要([ADR-0002](0002-qpdf-out-of-process-post-processor.md))。
