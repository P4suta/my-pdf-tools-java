# ADR-0009: Spring Boot Web レイヤー(`:webapp`)と subprocess 分離・デプロイ

- ステータス: 採用
- 日付: 2026-06-06
- 関連: [ADR-0001](../../tate-yoko-pdf/docs/adr/0001-hexagonal-ports-and-adapters.md)（ヘキサゴナル）, [ADR-0006](../../tate-yoko-pdf/docs/adr/0006-commons-cli-over-picocli.md)（反マジックの CLI 前例）, [ADR-0008](../../tate-yoko-pdf/docs/adr/0008-gradle-multi-module-split.md)（境界の物理化）
- 場所: 本 ADR はモノレポ横断の決定なのでリポジトリルート `docs/adr/` に置く。アプリ個別の ADR(`tate-yoko-pdf/docs/adr/`)はモノレポ統合前の名残で、今後の横断 ADR はここに集約する。

> **配布戦略の現状サマリ(前方ポインタ)**: 本 ADR は時系列の追記で育っているため、結論を先に置く。
> `:webapp` は **2 つの補完的な配布**を持つ — (1) ランタイム Docker イメージ(決定 6。常駐サーバ向けの**既定**)
> と、(2) 各 OS ネイティブの自己完結 jpackage app-image(2026-06-09 改訂で**追加**。Docker-free・JDK-free、
> 内部に pdfbook app-image を内包。非既定)。どちらかが他方を置換するものではない。配布の実務(ビルドモデル・
> OS 別手順・プロパティ規約)は [`docs/distribution.md`](../distribution.md) に集約。以下は決定の時系列記録。

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

## 改訂 (2026-06-06): Spring を「導入」から「使いこなし」へ

当初の `:webapp` は Spring を最小アダプタ(`spring-boot-starter-web` のみ)として導入したが、サーバ
フレームワークの中核能力は未活用だった。本改訂で、最外周封じ込め(ArchUnit)・カバレッジ床・
NullAway・single nullness vocabulary という同じ品質バーを保ったまま、本格運用形へ拡張する。**当初
明記した「メトリクスなし」等の決定を意図的に見直す**ものであり、黙った反転ではない。

### 追加でスコープ入りした決定

1. **エラーは RFC 9457 `ProblemDetail`**(`application/problem+json`)。手書き `ApiError` を置換し、
   安定した機械可読 `code` プロパティを併載(SPA はそれで分岐)。framework 例外も
   `spring.mvc.problemdetails.enabled` で統一。
2. **可観測性(Actuator + Micrometer)**。頭脳に framework を漏らさないため **framework-free な計測
   seam** を新設: `:webapp:port` の `QueueStats`(plain Java)を `BoundedConversionExecutor` が実装し、
   app 層の `PdfbookMetrics`(`MeterBinder`)/`MeteredConversionEngine`(所要時間+成否 Timer)/
   3 つの `HealthIndicator`(pdfbook バイナリ・work-dir・キュー)がそれを読む。`MeteredConversionEngine`
   の `outcome` タグは `Conversions` が DONE/FAILED を `convert()` の throw/return だけで決めるのと
   一致する。`/actuator` は `health,info,metrics,prometheus`。新 ArchUnit ルールで
   `io.micrometer..`/`actuate..`/`health..` を内側で禁止。
3. **構造化ログ + 相関 ID**。prod profile で Boot 4 内蔵 `logging.structured.format.console: ecs`、
   `Conversions.runConversion` がワーカスレッドの MDC に `jobId` を束ねる(try-with-resources で必ず
   除去 → 再利用ワーカに残らない)。Micrometer Tracing/OTel は単一並列ローカルツールに過大として不採用。
4. **OpenAPI/SpringDoc**(`springdoc-openapi-starter-webmvc-ui` 3.0.x = Boot 4/Jackson 3 系)。
   `/swagger-ui.html`、`@Operation`/`@Tag` 注釈。
5. **モダン実行モデル**: `spring.threads.virtual.enabled`(Boot 4/Java 25 のモダン化。SSE は元々非同期
   でスレッドを占有しない — 誇張しない)、`server.shutdown: graceful`(処理中の HTTP/SSE をドレイン。
   バックグラウンド変換は daemon ワーカなので守らない)、dev/prod profiles。
6. **設定の fail-fast**: `@Validated WebappProperties` + `@Positive`(queueCapacity/reaperIntervalMs)。
7. **HTTP 層の実テスト**: `spring-boot-starter-webmvc-test`(Boot 4 はスライスをモジュール分離)で
   `@WebMvcTest`(`MockMvcTester`)+ `@SpringBootTest` スモーク。カタログの JUnit 6.1/Mockito 5.23/
   AssertJ を最高版優先で維持。Spring Boot が持ち込む Logback と共有規約の slf4j-simple が衝突するため、
   当モジュールのみ test runtime から slf4j-simple を除外(prod と同じ Logback を使う)。
8. **単一成果物デプロイ**: SPA をランタイム Dockerfile の node ステージでビルドし bootJar の
   `static/` に同梱(Gradle は Java 専用のまま。dev コンテナに node は足さない)。

### 意図的な非ゴール(先送り決定。将来ポート差し替えで採用可)

- **認証(Spring Security)**: 既定 loopback のため当面不要。LAN 公開時の前提条件として先送り
  (公開時は prod の `management.endpoint.health.show-details` を `when-authorized` に下げ済み、
  別 loopback management port + 認証を足すこと)。なお prod でも `/actuator/{metrics,prometheus}` は
  公開のまま(`-p 127.0.0.1` 前提の個人ツールゆえ許容。LAN 公開時は actuator 全体を認証 or 別ポートへ)。
- **永続化**: 変換は ephemeral(再起動でジョブ消失を許容)。`JobStore` の差し替えで DB 化可能。
- **複数並列ワーカ**: pdfbook 自身が `-j` で全コア飽和するため 1 ワーカが妥当。
- **`spring-boot-configuration-processor`**: 当モジュールの `-Xlint:all -Werror` 下で
  「No processor claimed」警告が出てビルドを落とすため不採用(均一ゲートに穴を開けない)。`@Validated`
  のみ採用。
- **Micrometer Tracing / OpenTelemetry**: 上記 3 のとおり過大。

## 改訂 (2026-06-09): Web も各 OS ネイティブ app-image で自己完結配布(Docker と並立)

CLI 4 機能(register/despeckle/pdfbook/tate)は PR #10 で「Linux+Docker 開発・各 OS では JRE 同梱/
ネイティブ同梱の Docker-free・JDK-free な jpackage app-image で利用」となり 3-OS CI で恒常検証されて
いたが、**Web 機能だけがこの体系の外**だった(配布はランタイム Docker と `java -jar` のみ)。本改訂で
`:webapp` も CLI と対称な第一級の自己完結 app-image を**追加**する(決定 6 のランタイム Docker は一級の
まま — これは置換ではなく補完)。

- **棄却案「jpackage でサーバも自己完結配布」(代替案節)を緩和**: 「常駐サーバに不向き・ネイティブ同梱は
  冗長」という判断は LAN 常駐サーバとしては今も妥当なので **Docker を既定の推奨**に残す。一方で、CLI と
  同じ「単一フォルダを置いて実行」の Docker-free 配布をポートフォリオ/ローカル用途に**非既定で提供**する
  価値が上回ったため、自己完結 app-image を追加する(冗長さは Docker-free のための意図的トレードオフ)。
- **合成(nesting)設計**: webapp サーバ自身はネイティブを一切リンクしない(pdfbook を subprocess 起動
  するだけ)。よって app-image は実証済みの **pdfbook app-image を `$APPDIR/tools/pdfbook/` に内包**し、
  サーバを正準キー `-Dp4suta.pdfbook.path`(ToolPath が解決する既存の `p4suta.<tool>.path` 規約)で
  そこへ向ける。決定 4 の subprocess 分離・`:pipeline` へのコンパイル依存ゼロは不変 — 内包は**配布時のみ
  の variant 対応 configuration アーティファクト共有**で、コンパイル/ランタイム classpath には pipeline
  の jar を一切載せない(ArchUnit + classpath で確認)。
- **規約の一般化**: 「兄弟の自己完結 app-image を内包する」を webapp 特例でなく規約の一般機能
  `selfContainedApp { bundledApp(...) }` として実装(`NativePlatform` に per-OS の `$APPDIR`/ランチャ・
  パス、`assembleAppImage` が jpackage **後**に自前出力 dir `build/dist-app/` へ内包コピー、macOS は外側
  バンドルを ad-hoc 再署名)。jpackage の `--input` には混ぜない(クラスパス汚染・二重署名の回避)。
- **決定 8(SPA を node ステージでビルド・Gradle は Java 専用)を見直し**: Docker-free・cross-OS で
  app-image を作るには SPA も標準ツールチェーンで作る必要があるため、フロントエンドを node-gradle
  プラグインで**第一級 Gradle モジュール `:webapp:frontend`** 化し、ビルド成果物を bootJar の `static/`
  に配線(`src/main/resources/static/` への書き込みスメルを解消)。`download=false` で dev イメージ/CI の
  PATH 上の node/pnpm を使い(corepack 不使用)、「Gradle はツールチェーンを自動ダウンロードしない」不変
  条件は維持。`check` は node 非依存のまま、`bootJar`/`package` のみ node 必須。
- **jpackage 入れ子の落とし穴(再利用知見)**: jpackage app-image ランチャは起動時に再起動マーカー
  `_JPACKAGE_LAUNCHER` と動的リンカパス(Linux `LD_LIBRARY_PATH` / macOS `DYLD_LIBRARY_PATH`)を
  setenv する。サーバ(外側ランチャ)が内側の pdfbook ランチャ(同じ jpackage 実装)を spawn すると、この
  ペアを継承した子が「再起動の途中」と誤認しアプリ引数を JVM オプション扱いして起動失敗する。
  `SubprocessConversionEngine` が子環境からこの 3 変数を除去して回避(app-image 以外では未設定なので
  no-op)。
- **CI 実証**: `distribution.yml` に Linux(dev イメージ内)leg と `dist-webapp-crossos`(Win/mac)leg を
  追加。いずれも **サーバを PATH 空で起動 → `/actuator/health` UP → 実 PDF を投入 → DONE まで状態
  ポーリング → 結果が `%PDF`+`Linearized`** を assert(`dist-smoke.{sh,ps1}`)。pdfbook leg と同等の深さで
  全鎖(バンドル JRE サーバ + 内包 pdfbook + そのネイティブ + qpdf 線形化)を検証する。

## 改訂 (2026-06-09): WWW 公開ハードニング(リバースプロキシ前提)+ エラー詳細漏洩の是正

Windows app-image での実 PDF 変換が exit 70(INTERNAL)で失敗し原因が辿れなかった件の調査で、
**変換失敗時に `RunFailed.message` / `JobStatusResponse.errorMessage`(サブプロセス stderr・絶対パスを
含みうるサーバー内部詳細)が SSE・REST 経由でブラウザへ無条件到達**していることが判明。決定 1(エラーは
presentation-free。クライアントは安定 `kind` からローカライズ)の**実装欠陥**であり、是正する。あわせて
「個人 LAN ツール」前提から **WWW 公開にも耐える**方針へ引き上げる(認証・境界モデルは本 ADR の
リバースプロキシ前提を踏襲。Spring 本体はミニマルのまま)。

- **境界の不変条件を確立**: 完全な診断詳細はサーバー側(ログ + `Job` レコード)にのみ置き、HTTP 境界では
  `kind` のみをクライアントへ渡す。`shared/progress` の `JsonlProgressCodec`/kernel は**不変**(pdfbook→
  サーバー間の進捗チャネルは `message` を必要とし、サーバーログの源泉)。サニタイズは webapp の境界 2 点:
  `SseProgressPublisher` の単一 `send()`(全送出経路のチョークポイント)で `RunFailed.message` を空にし、
  `JobStatusResponse` から `errorMessage` を除去。フロントは未知 kind 時に生詳細へフォールバックしない。
- **診断はサーバー側で強化**: `Conversions` が捕捉済み `RunFailed` の真因を `log.warn` し、
  `SubprocessConversionEngine` は pdfbook を `--verbose` 起動 + 子の stdout/stderr を一時ファイルへ捕捉、
  非 0 終了時に末尾をログへ。RunFailed を出せず死ぬ経路でも真因が残る。
- **CSRF(`SameOriginCsrfFilter`)**: リバースプロキシの Basic 認証は CSRF を防がない(資格情報が自動付与
  される)。`POST /api/v1/jobs` は `multipart/form-data` でプリフライト無しのため、状態変更系は
  `Origin`/`Referer` を `Host` に照合して拒否(OWASP 方式。ヘッダ無しの非ブラウザ client は通過)。
- **actuator を別 loopback 管理ポートへ**(本 ADR の先送り決定「別 loopback management port」を実装):
  prod profile で `management.server.{address:127.0.0.1, port:8081}`。`/actuator/*` は公開コネクタ
  (0.0.0.0:8080)から消える。loopback 専用化に伴い metrics/prometheus と health 詳細を管理ポートで復帰。
- **SSE emitter のジョブ単位上限**: 1 ジョブの同時ストリームを上限化(無制限の emitter 蓄積を防止)。
- **リバースプロキシ参照設定**: `deploy/reverse-proxy/`(Caddyfile + README)。TLS 自動 ACME・Basic 認証・
  ボディ上限(512MB 整合)・レート制限(plugin)・`/actuator` 不通過。常駐 Docker(prod)の前段に置く。
- **自己完結 app-image は WWW 公開対象外**: プロキシを持たず default(loopback)profile で動くため、
  LAN/ローカル desktop 用途と明記。公開耐性の対象は常駐 Docker(prod)+ リバースプロキシ。
