# ADR-0006: Picocli から Apache Commons CLI へ移行

- ステータス: 採用
- 日付: 2026-06-03
- 関連: README の使い方節, バージョニング方針([architecture.md](../architecture.md))

## コンテキスト

CLI の引数解析ライブラリを選ぶ。初期実装は Picocli を用いていた(削除済みの技術設計書が記録)。

## 決定

Apache Commons CLI 1.11.0 へ移行する。`SpreadCommand` がオプション定義・解析・ヘルプ表示を担い、解析後の
意図は `CliArguments` レコードに集約する。

## 根拠

- 解析に必要な機能はシンプルで、Commons CLI で十分。
- アノテーション駆動・リフレクションを避け、jlink/native 配布での挙動を素直に保てる。
- 依存と起動コストを抑えられる。

## 検討した代替案

- **Picocli の継続**: 高機能だがアノテーション/リフレクション前提で、この規模には過剰。
- **自前パーサ**: 車輪の再発明で、エッジケース(`--` 区切り等)の扱いを誤りやすい。

## 影響

- CLI は公開契約。v2.0.0 で非推奨エイリアス `--cover-single` を削除し、`--first-page cover` に一本化
  した(破壊的変更 → メジャー更新)。
- 引数解釈(文字列→ドメイン enum、`--first-page`×方向の解決)は `CliArguments` に閉じ込め、
  `SpreadCommand` はパース振り分けと変換オーケストレーションに専念する。
