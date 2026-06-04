# ADR-0007: PDF/A-2b をベストエフォートで出力

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0002](0002-qpdf-out-of-process-post-processor.md), [ADR-0003](0003-pdfbox-as-pdf-engine.md)

## コンテキスト

長期保存向けに PDF/A 出力(`--pdf-a`)を提供したい。ただし最終的な PDF/A 適合性は、埋め込む元ページの
内容(フォント埋め込み・色空間 等)に依存し、本ツールだけでは保証しきれない。

## 決定

`SpreadDocument.finalizePdfA()` で **PDF/A-2b の適合「構造」のみ**を付与する: sRGB 出力インテントと、
pdfaid/Dublin Core/Adobe PDF/XMP Basic を含む XMP パケット。XMP は `applyMetadata` が設定した情報辞書を
ミラーして作るため、Info/XMP の一貫性が構造的に保たれる。実適合は元ページ依存である旨を Javadoc と
README に明記する(ベストエフォート)。

## 根拠

- 構造付与までがツールの責務として妥当な境界。元コンテンツの再エンコードまでは踏み込まない。
- sRGB 出力インテントは JDK 同梱の sRGB プロファイルを使い、.icc リソース同梱を避ける(`java.desktop`
  は PDFBox で既に同梱)。
- linearize 後も適合を保つため、qpdf の `--newline-before-endstream` を併用する([ADR-0002](0002-qpdf-out-of-process-post-processor.md))。

## 検討した代替案

- **完全な PDF/A 変換(フォント埋め込み等まで保証)**: 元 PDF の任意コンテンツを正規化する大掛かりな
  実装が必要で、スコープ過大。
- **PDF/A を提供しない**: 保存用途の需要に応えられない。

## 影響

- PDF/A-2 は PDF 1.7 基盤。出力版が 1.7 でない場合は `finalizePdfA` が拒否する。
- veraPDF(独立検証器)による end-to-end テストで、構造が実際に PDF/A-2b として読めることを担保する。
- PDF/A 書き込みの密度を `PdfAWriter` に切り出し、`PdfBoxSpreadDocument` は版ガードと例外変換のみ保持。
