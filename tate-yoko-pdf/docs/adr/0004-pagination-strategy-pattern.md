# ADR-0004: ページネーションを Strategy パターンで表現

- ステータス: 採用
- 日付: 2026-06-03
- 関連: [ADR-0001](0001-hexagonal-ports-and-adapters.md)

## コンテキスト

「ページ1がどの面で開くか」によって、ページの対の作り方が変わる。

- **STANDARD**: 1·2, 3·4, …(ページ1は読み方向の先頭側)
- **COVER**: [1], 2·3, 4·5, …(ページ1を単独表紙に)
- **LEADING_BLANK**: [空白|1], 2·3, …(先頭に空白を挟む)

CLI の `--first-page right|left|cover` は、読み方向(RTL/LTR)と組み合わせてこのいずれかへ解決される。
この分岐をどう構造化するかを決める。

## 決定

`sealed interface PaginationStrategy` と3実装(`StandardPagination` / `CoverSinglePagination` /
`LeadingBlankPagination`)で表現し、`FirstPageMode` から `PaginationStrategyFactory` で選択する。各戦略は
`List<PagePairSpec>` を返し、`PagePairSpec` は `Pair(i, i+1)` / `Single(i, half)` の sealed ADT。

## 根拠

- 分岐がポリモーフィズムに置き換わり、`if`/`switch` の散在を避けられる。
- 各戦略の不変条件(全ページが過不足なく1度ずつ現れる・対は連続する 等)を property-based test で
  独立に検証できる。
- 新しい開き方の追加が局所的。

## 検討した代替案

- **巨大な switch 1個**: ページ対生成のロジックが1メソッドに集中し、テストと拡張がしにくい。
- **boolean フラグの組み合わせ**: 「cover かつ leading-blank」のような無効状態を型で排除できない。

## 影響

- 戦略選択はアプリ層の関心事とし、`domain.strategy` を直接 new するのはアプリ層だけ、という制約を
  ArchUnit で残している(モジュール分割後も唯一残るレイヤ内ルールの一つ)。
- v2.0.0 で、読み方向×サイドの解決(`OpeningSide` + `FirstPageMode.fromSide`)を CLI からドメインへ
  引き上げ、同じ事実の4重定義を解消した。
