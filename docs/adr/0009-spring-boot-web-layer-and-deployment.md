# ADR-0009: Spring Boot Web レイヤー(`:webapp`)と subprocess 分離・デプロイ

- ステータス: 採用
- 日付: 2026-06-06
- 関連: [ADR-0001](../../tate-yoko-pdf/docs/adr/0001-hexagonal-ports-and-adapters.md)（ヘキサゴナル）, [ADR-0006](../../tate-yoko-pdf/docs/adr/0006-commons-cli-over-picocli.md)（反マジックの CLI 前例）, [ADR-0008](../../tate-yoko-pdf/docs/adr/0008-gradle-multi-module-split.md)（境界の物理化）
- 場所: 本 ADR はモノレポ横断の決定なのでリポジトリルート `docs/adr/` に置く。アプリ個別の ADR(`tate-yoko-pdf/docs/adr/`)はモノレポ統合前の名残で、今後の横断 ADR はここに集約する。

## コンテキスト

PDF 処理コア(register / despeckle / tate-yoko-pdf と統合パイプライン pdfbook)を Web から使えるようにしたい。利用形態は個人・ローカル(家庭内 LAN 含む)＋ポートフォリオ作品。重要な制約は、**コアは今後も大きく育つため、Web 機能が付け焼き刃になると拡張性・持続可能性の足かせになる**こと。よって機能より先に「コアと同格の一級アーキテクチャ」を要件とする。

技術的事実: pdfbook はネイティブ依存が重い(Leptonica を FFM 経由、pdfimages/qpdf/jbig2enc/libwebp、フォント)。1 冊の変換は数秒〜数分の長時間処理で、各処理は `-j`=全コアで走る。

## 決定

1. **Web 機能を新トップレベル機能 `:webapp` の5層ヘキサゴン**(domain/port/application/infrastructure/app)として実装。register 等4機能と同格。
2. **フレームワークは Spring Boot 4.0**(Spring Framework 7、Java 25 第一級サポート)。ポートフォリオ価値と本プロジェクトの Java 25 に合致。
3. **Spring は最外周 `:webapp:app` だけ**に封じ込める(ADR-0006 が Commons CLI を `:*:app` に閉じたのと同形)。ジョブのライフサイクル状態機械・キュー方針・進捗集約・TTL・オプション検証といった「頭脳」はフレームワーク非依存の domain/application に置き、PDF 機能と同じ品質ゲート(NullAway / ArchUnit / カバレッジ床)で律する。ArchUnit が「内側への Spring 混入禁止」を強制。
4. **pdfbook は subprocess 分離で実行**。`:webapp:infrastructure` の `SubprocessConversionEngine` が、`:shared:process` の ToolPath で解決した pdfbook バイナリを子プロセス起動する。結果 `:webapp` は `:pipeline:*` / `:tate-yoko-pdf:*` に**コンパイル依存ゼロ**(ArchUnit で強制)。pdfbook は qpdf 等と同じ「外部ツール」。
5. **進捗は構造化イベントプロトコル**。共有モジュール `:shared:progress` が唯一のイベント語彙(`ProgressEvent` sealed + JSONL コーデック)を持つ。pdfbook CLI に `--progress-file <path>` を追加し、進捗/終端イベントを JSONL で出力。サーバはそのファイルを**バイトオフセット増分読み + プロセス終了後の最終ドレイン**で追従し SSE に再配信。ログのスクレイピングではなく機械可読な契約。
6. **デプロイはランタイム Docker** を一級とする。jpackage ネイティブイメージは常駐サーバに不向き。既存 dev イメージのネイティブ群を持つランタイムイメージ + JRE + `bootJar` + pdfbook で動かす。ローカルは `bootRun` / `java -jar`。

## 根拠

- ヘキサゴンの「頭脳」が Spring 非依存なので、同期フェイク executor で変換タスクをインライン実行して全分岐を単体テストでき、PDF 機能と同じカバレッジ床(domain 0.95/0.90, application 0.90/0.80)を満たせる。Spring のリフレクション/自動構成は NullAway・ArchUnit・床と相性が悪く、最外周に閉じることでゲートを保てる。
- subprocess 分離: Leptonica の FFM や外部ツールのネイティブクラッシュが**常駐サーバ JVM を巻き込まない**(再起動可能な失敗に留まる)。サーバ JVM は `--enable-native-access` も不要。
- 構造化プロトコル: 終端 `RunFailed{kind,message}` を同じ経路で運ぶので、終了コードだけに頼らずリッチなエラーをユーザに出せる。バイトオフセット方式は追記の合体や終了直前の最終行・UTF-8 マルチバイト境界に対して堅牢。
- SSE は**購読時リプレイ**(ジョブ単位バッファ + `computeIfAbsent` の同一モニタ同期)で「submit 直後購読」レースでもイベントを失わない。

## 検討した代替案

- **軽量サーバ(Javalin 等)**: コードベースの反マジック志向には整合するが、ユーザの明示的なポートフォリオ/学習目的(Spring Boot)を満たさない。Spring を最外周に閉じれば厳格ゲートも保てるため、Spring Boot は「論外」ではなく適切と判断。
- **in-process 実行**(`:pipeline:application` を直接 JVM 内で呼ぶ): 直接の進捗取得は容易だが、ネイティブクラッシュがサーバを落とすリスク。`ConversionEngine` ポートの差し替えで将来も選べるが、既定は subprocess。
- **stdout スクレイピングで進捗**: flush/占有/`-o -` の問題＋脆い。構造化ファイルプロトコルを採用。
- **jpackage でサーバも自己完結配布**: 常駐サーバには不向きでネイティブ群の同梱も冗長。ランタイム Docker を採用。

## 影響

- 新モジュール `:webapp:{domain,port,application,infrastructure,app}` と `:shared:progress`。`settings.gradle.kts` は forEach 自動生成ではなく明示 include(`:webapp:app` が Spring のため)。
- pipeline 側に加法的変更: `Source`/`Sink` の `name()`、`PipelineRunner` の `ProgressSink` オーバーロード、CLI `--progress-file`。既存挙動は不変。
- 配布物が増える: `:webapp:app` は `bootJar`(`application`/`shadow` ではなく Spring Boot プラグイン)。ランタイム Dockerfile は dev/build 用とは別に新設。
- 既定 `server.address: 127.0.0.1`(LAN 公開はオプトイン)、`JobId` は安全トークン制限(パストラバーサル防止)、アップロードは content-type/サイズ検証。
