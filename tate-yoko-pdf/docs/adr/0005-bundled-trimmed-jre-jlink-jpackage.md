# ADR-0005: jlink + jpackage でトリム JRE を同梱

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0002](0002-qpdf-out-of-process-post-processor.md), [ADR-0003](0003-pdfbox-as-pdf-engine.md)

## コンテキスト

エンドユーザー(必ずしも Java 環境を持たない)に CLI を配布したい。素の fat JAR では JRE インストールを
要求してしまう。一方 GraalVM native-image は、PDFBox が依存する AWT 周りで不安定だった。

## 決定

jlink で必要モジュールだけのトリム JRE を組み、jpackage で OS ネイティブな app-image(ランチャ + JRE +
shadow JAR)を生成する。qpdf バイナリも同梱する。Beryx プラグインは Gradle 9.x 非互換のため、jlink/
jpackage を直接呼び出す。

## 根拠

- Java 未インストールでも動く自己完結配布物になる。
- `--add-modules` を必要分(`java.base`, **`java.desktop`**, `jdk.zipfs` 等)に絞り、サイズを抑える。
  `java.desktop` は PDFBox(AWT)に必須。
- `-XX:MaxRAMPercentage=75.0` でホスト/コンテナ cgroup に比例したヒープにする。

## 検討した代替案

- **GraalVM native-image**: 起動は速いが PDFBox の AWT 依存で不安定。不採用。
- **素の fat JAR**: ユーザーに JRE を要求する。配布体験が悪い。

## 影響

- jpackage は OS 別ランチャを生成するため、各 OS のホストでビルドする(CI の3OSマトリクス)。
- 配布物の出力は v2.0.0 のモジュール分割で `app/build/dist-jpackage/` に移った。
- ランタイム計測(`RuntimeBenchmark`)と smoke テスト(`SmokeCheck`)は、この同梱ランチャを子プロセスとして
  起動して検証する。
