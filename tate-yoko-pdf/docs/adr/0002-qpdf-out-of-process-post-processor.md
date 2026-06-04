# ADR-0002: qpdf をアウトプロセス後処理として採用

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0003](0003-pdfbox-as-pdf-engine.md), [ADR-0007](0007-pdf-a-2b-best-effort-approach.md), [ADR-0005](0005-bundled-trimmed-jre-jlink-jpackage.md)

## コンテキスト

出力 PDF に対して、PDFBox だけでは実現できない/しにくい仕上げが必要だった。

1. **Linearize(Fast Web View)**: バイト列を再配置し、ストリーミング表示や HTTP Range 取得を可能にする。
2. **ヘッダバイトの版書き換え**: PDFBox 3.0.7 の `setVersion` は 1.4 以上ではカタログ `/Version` しか
   更新せず、`%PDF-x.x` ヘッダバイトは据え置く。
3. **`--newline-before-endstream`**: PDF/A(ISO 19005 6.1.7.1)が要求する endstream 前の EOL を保証。
   linearize は再パックでこのマーカを落とすため、後段で再付与する必要がある。

## 決定

`PdfPostProcessor` ポートを設け、`QpdfLinearizer` アダプタが **qpdf バイナリをアウトプロセス**で1パス
呼び出し、上記3点をまとめて適用する。qpdf が見つからない場合は `PdfPostProcessor.noOp()` にフォール
バックし、警告を1回出してパイプライン全体は壊さない。

## 根拠

- linearize とヘッダ書き換えは純 Java(PDFBox)では実現困難。qpdf は枯れた定番ツール。
- ポート越しなので、ドメイン/アプリは qpdf の存在を知らない。
- `--replace-input` は警告終了(コード3)時に `.~qpdf-orig` バックアップを残し出力ディレクトリを汚す
  ため、明示的な出力ファイル + `Files.move` で同等の linearize 結果を得つつディレクトリを清潔に保つ。

## 検討した代替案

- **純 Java で linearize**: 相当量の自前実装が必要で保守負担が大きい。
- **qpdf を Java ライブラリとして埋め込む**: qpdf に安定した JVM バインディングは無い。

## 影響

- qpdf バイナリを配布物に同梱する必要が生じた([ADR-0005](0005-bundled-trimmed-jre-jlink-jpackage.md))。
  解決順は「同梱 → PATH → noOp」。
- プロセス起動の生配管は `ProcessRunner` に切り出し、qpdf 固有の引数・終了コード方針(0/3 を受理)は
  `QpdfLinearizer` に残している。
