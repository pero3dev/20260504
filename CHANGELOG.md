# Changelog

このリポジトリの主要な変更履歴。書式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に準拠し、本プロジェクトは [Semantic Versioning](https://semver.org/lang/ja/) を採用予定(現在は `0.0.1-SNAPSHOT` 単一バージョン)。

詳細な設計判断は `docs/adr/` の各 ADR を、各機能の意図はコミットメッセージを参照。

---

## [Unreleased]

### Highlights

13 サービス(共通基盤 9 + 業態 4)のスキャフォールディング、業態 → Inventory Core の Saga 連結 4 経路 + Manufacturing 完成品 INBOUND 失敗時補償、業態 OUTBOUND/Cancel フローの完成、Workflow SLA 中央タイマ + 承認アクション自動 step advance、J-SOX 監査(WORM + ハッシュチェーン + Merkle anchor + S3 Object Lock 二重保管)実装、Pact 契約テスト Phase 5 + ADR-0021 で本番ホスティング決定、 Notification SES 実 sender、 identity-broker テナント lifecycle 管理、 integration-hub S3Destination、 Frontend monorepo(Turborepo + 全 4 業態 BFF + Web スケルトン + CI 緑化)までを完了。13/13 サービス + 21 ADR + 8 E2E テストケース + Frontend 全 4 業態スケルトン(BFF + Web 各 4)。

### Added

#### 共通基盤サービス(9/9)

- **identity-broker**: JWT 発行 + テナント解決(ADR-0007)。Cognito フェデレーションは将来。
- **master-data**: SKU / Location / Partner マスタ。Bridge 方式 + Outbox。コミット [`bcd58c2`](https://github.com/pero3dev/20260504/commit/bcd58c2)
- **inventory-core**: 在庫状態の唯一の書込権威(ADR-0002, ADR-0004)。Reserve / Ship / Receive / Release を備える。
- **inventory-read-model**: Kafka 購読 → Redis 投影、CQRS Read 側(ADR-0004)。
- **audit-service**: AOP 取込 + SHA-256 ハッシュチェーン + 日次 Merkle anchor + DB WORM(ADR-0008、commit [`280e174`](https://github.com/pero3dev/20260504/commit/280e174))。
- **notification**: Kafka 駆動の通知配信(送信器 port 抽象)。コミット [`59876ab`](https://github.com/pero3dev/20260504/commit/59876ab)
- **analytics**: 業態系イベント集計 → daily_order_summary(Pool 方式)。コミット [`4095175`](https://github.com/pero3dev/20260504/commit/4095175)
- **workflow**: Saga オーケストレータ MVP(ADR-0015 の判定基準で導入条件付き)。コミット [`4e26a5d`](https://github.com/pero3dev/20260504/commit/4e26a5d)
- **integration-hub**: 外部連携(EDI / S3 / SFTP / EC)の足場。MVP は CSV 1 アダプタ。コミット [`3e46a64`](https://github.com/pero3dev/20260504/commit/3e46a64)

#### 業態サービス(4/4)

- **retail-ec**: B2C 注文受付 + `retail.order.placed.v1` 発行(コミット [`5d2668d`](https://github.com/pero3dev/20260504/commit/5d2668d))。L2 で出荷確定フロー追加(コミット [`70236a6`](https://github.com/pero3dev/20260504/commit/70236a6))。
- **tpl**: 3PL 入出庫管理 + `tpl.stock.movement.v1`。コミット [`12942f3`](https://github.com/pero3dev/20260504/commit/12942f3)
- **wholesale**: B2B 取引先別契約価格 + 受注 + 出荷フロー。コミット [`52d0551`](https://github.com/pero3dev/20260504/commit/52d0551) / [`853a168`](https://github.com/pero3dev/20260504/commit/853a168)
- **manufacturing**: BOM + WorkOrder。L3 で完成品 INBOUND まで閉じる。コミット [`8fe1f9d`](https://github.com/pero3dev/20260504/commit/8fe1f9d) / [`d226973`](https://github.com/pero3dev/20260504/commit/d226973)

#### 業態 → Inventory Core の Saga 配線(全 4 経路)

| 業態 | 経路 | コミット |
|---|---|---|
| Retail/EC | Phase 1+2(reserve + 補償) | [`65e9c60`](https://github.com/pero3dev/20260504/commit/65e9c60) [`41c5467`](https://github.com/pero3dev/20260504/commit/41c5467) |
| 3PL | D6(stock movement → reserve+ship/receive) | [`ea282e2`](https://github.com/pero3dev/20260504/commit/ea282e2) |
| Wholesale | D9(reserve + 補償) | [`3d243d3`](https://github.com/pero3dev/20260504/commit/3d243d3) |
| Manufacturing | D10(部品 reserve+ship + 補償) | [`a0c93ca`](https://github.com/pero3dev/20260504/commit/a0c93ca) |

#### 業態 OUTBOUND/Cancel フロー(L1/L2/L3 + 取消強化)

- **L1 Wholesale**: 出荷確定 → `wholesale.order.shipped.v1` → ship。コミット [`853a168`](https://github.com/pero3dev/20260504/commit/853a168)
- **L2 Retail/EC**: 出荷確定 → `retail.order.shipped.v1` → ship。コミット [`70236a6`](https://github.com/pero3dev/20260504/commit/70236a6)
- **L3 Manufacturing**: WorkOrder.complete → `manufacturing.work_order.completed.v1` → 完成品 receive。コミット [`d226973`](https://github.com/pero3dev/20260504/commit/d226973)
- **業務取消 → reserved 解放**: `<業態>.order.cancelled.v1` → `Inventory.release(qty)`。`Order.cancel()` と `Order.cancelAfterReservationFailure()` を分離(ADR-0018)。コミット [`e1b2ca2`](https://github.com/pero3dev/20260504/commit/e1b2ca2)

#### 監査ハードニング(D3、ADR-0008)

- **AOP @Auditable** で全状態変化をキャプチャ(commons-audit)
- **SHA-256 ハッシュチェーン** + チェーン整合性検証 REST(`AuditChainVerifier`)
- **日次 Merkle anchor** + 検証 REST(`Sha256MerkleTreeCalculator` + `ComputeDailyMerkleAnchorService`)
- **DB レベル WORM トリガ** で `audit_record` / `audit_merkle_anchor` の UPDATE/DELETE 拒否

#### ADR(18 本)

| # | タイトル | 状態 |
|---|---|---|
| 0001-0013 | プロジェクト基本方針 | Accepted(本セッション以前) |
| [0014](docs/adr/0014-cross-service-e2e-deferred-to-local-only.md) | Cross-service E2E deferred to local-only | Accepted |
| [0015](docs/adr/0015-saga-choreography-as-default-orchestration-on-demand.md) | Saga choreography as default | Accepted |
| [0016](docs/adr/0016-per-business-context-compensation-topics.md) | Per-business-context compensation topics | Accepted |
| [0017](docs/adr/0017-reserve-vs-reserve-ship-selection.md) | Reserve vs Reserve+Ship 使い分け | Accepted |
| [0018](docs/adr/0018-cancel-vs-cancel-after-reservation-failure.md) | cancel メソッド使い分け | Accepted |

#### CI / Test 強化

- **KafkaIntegrationE2ETest** 8 ケース — 業態 4/4 全成功パス + Master 投影 + 認証 + 直接 reserve(コミット [`953cd98`](https://github.com/pero3dev/20260504/commit/953cd98) / [`c6dd5c0`](https://github.com/pero3dev/20260504/commit/c6dd5c0) / [`524a28b`](https://github.com/pero3dev/20260504/commit/524a28b) / [`a497f62`](https://github.com/pero3dev/20260504/commit/a497f62))
- **mvn -T 1C 並列化** — verify 時間 ~50% 短縮(2:16 → 1:10)。コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1)
- **Pact consumer-driven 契約テスト MVP** — `wholesale.order.placed.v1` の Consumer 期待形式を Pact ファイル出力。コミット [`025803f`](https://github.com/pero3dev/20260504/commit/025803f)
- **Pact Phase 2.1 — Provider verify** — Wholesale 側の `SalesOrderPlacedEvent` が Consumer 契約を満たすか verify(コミット [`79c794b`](https://github.com/pero3dev/20260504/commit/79c794b))。
- **Pact Phase 2.2 — 4 経路の契約追加** — `retail.order.placed.v1` / `wholesale.order.shipped.v1` / `manufacturing.work_order.released.v1` / `tpl.stock.movement.v1` の Consumer/Provider 契約(コミット [`ed01832`](https://github.com/pero3dev/20260504/commit/ed01832))。
- **Pact Phase 3 — Broker + can-i-deploy** — 1) `infra/pact-broker/` に local Broker docker-compose + README、2) `inventory-core` に `pact:publish` Maven plugin、3) `.github/workflows/pact-broker.yml` で main push 時 publish + PR 時 can-i-deploy(secret 未設定時は skip)。 ADR-0019 Phase 3 セクション追加。
- **Pact Phase 3.5 — Provider verify の Broker 化** — 各 Provider test を `*ProviderPactBase`(共通) / `*ProviderPactTest`(Folder source) / `*ProviderBrokerPactTest`(Broker source、 `PACT_BROKER_URL` env でゲート)の 3 ファイル構成に分離。 `pact.verifier.publishResults=true` で verify 結果を Broker に publish back。 `pact-broker.yml` を `publish → provider-verify(4 並列 matrix) → can-i-deploy` の 3 段 job に再構成。 local Broker で round-trip 確認済(matrix API: `deployable=true, success=4, failed=0`)。
- **Pact Phase 4 — matching rule strict 一致を緩和** — Consumer Pact test を `expectsToReceiveMessageInteraction(name, i -> i.withContents(c -> c.withContent(payload)))` の V4 native API へ全 5 経路で書き換え。 これで `PactDslJsonBody` 上の `numberType / stringType / integerType / minArrayLike / stringMatcher` がすべて pact JSON の `matchingRules.body` に propagate される。 旧 `with(Map.of("message.contents", payload))` 経路は matching rule を **黙って捨てる** 落とし穴で、 ADR-0019 の "Known Limitation" として記録していたが Phase 4 で根治。 manufacturing Provider を 2 components 返却に戻す副作用付き。
- **Pact Phase 4.5 — `LambdaDsl` 全面移行** — Consumer Pact test を `PactDslJsonBody` チェーン形式から `LambdaDsl.newJsonBody(o -> {...})` ネストラムダ形式へ 5 経路すべて書き換え。 ネスト構造(`items[].*` など)がインデントで自然に表現され、中間変数の `itemTemplate` 等が消えた。 機能等価(matchingRules パス完全一致、 Folder/Broker 両経路で Provider verify 緑)、可読性向上のみ。
- **Pact Phase 5 — Consumer version selectors の本格運用** — 各 ProviderPactBase に `@PactBrokerConsumerVersionSelectors` を追加。 `mainBranch()`(プロダクション safety net)+ `deployedOrReleased()`(後方互換 safety)+ `branch(<provider branch>)`(PR 連動)の 3 selector で Broker から Consumer pact を取得。 `pact.consumer.branch` で publish に branch metadata を付与、 `pact-broker.yml` を branch-aware に。 Pact-JVM 4.6 の `matchingBranch()` バグ(`providerVersionBranch` 未付与で 400)を `branch(System.getProperty(...))` で回避。 main / pr-N 両ケースで Broker round-trip 確認済。
- **ADR-0021 Phase 1 — Pact Broker 本番デプロイ用 manifests** — `infra/pact-broker/k8s/`(Namespace / SA(IRSA)/ ConfigMap / Secret(External Secrets template)/ Deployment(2 replicas, RuntimeDefault seccomp, topology spread)/ Service(ClusterIP)/ Ingress(ALB internal, ACM)/ HPA(2-3, CPU 60%)/ NetworkPolicy(ALB → broker / broker → Aurora 5432 + DNS)) + `argocd/application.yaml`(GitOps)+ `db/001-create-pact-broker-db.sql`(Aurora-C 切り出し)+ README に Step 1〜8 ランブック。 ACM ARN / IRSA ARN / VPC CIDR / DB host は placeholder で、本番 PR 時に環境別値で patch する想定。 全 11 manifests が YAML parse 緑。
- **ADR-0021 Phase 2 — Cognito SSO 連携(人間 UI 経路)** — `ingress.yaml` を `ingress-ui.yaml`(`pact-broker.internal.example.com`、 ALB Cognito auth、 8h session)+ `ingress-api.yaml`(`pact-broker-api.internal.example.com`、 Basic Auth 直結)に分割。 同一 ALB を `IngressGroup` で共有。 Pact Broker 自体の Basic Auth は据え置き(二段認証 UX を許容)。 CI は API hostname に切替えるだけで Basic Auth credential 不変。 README に Phase 2 ランブック(Cognito User Pool App Client 作成 → ingress patch → DNS 追加 → GitHub secret 更新)。 シームレス SSO(JWT → Basic Auth 注入)は Phase 2.5 候補で後送り。
- **ADR-0021 Phase 2.5 — nginx sidecar による完全シームレス SSO** — Pact Broker pod に `nginx:1.27-alpine` sidecar を同居させ、 ALB Cognito auth 通過後の UI 経路で `Authorization: Basic <read-only-cred>` を自動注入。 Phase 2 の二段認証 UX(Cognito + Basic Auth ダイアログ)を解消し、 ユーザは Cognito 1 回サインインだけで Pact Broker UI が即時表示される。 CI 経路は sidecar バイパスで Pact Broker へ直接到達(別 Service named port)。 注入する credential は read-only 固定で Phase 3(社内全員 read-only 公開)と整合。 oauth2-proxy ではなく nginx を選んだのは、 ALB が既に Cognito auth を完了させており OIDC handshake 重複が不要なため(image 5MB / memory 32MB の軽量)。
- **ADR-0021 Phase 3 — UI を engineering org 全員に read-only 公開** — `pdb.yaml`(PodDisruptionBudget、 `minAvailable: 1`)を追加し voluntary disruption(node drain / cluster autoscaler / rolling update)時に常時 1 replica を保持。 NetworkPolicy に sidecar 9293 ingress を補完(Phase 2.5 の追加漏れを塞ぐ)。 README に Phase 3 デプロイ手順 + Cognito access policy(全 eng org or engineering group)+ 社内告知テンプレ + 運用ノート(load 想定 / Aurora-C 負荷 / 退職者アクセス取り消し)を追加。 注入 credential が read-only 固定なので audience 拡大しても write 操作は CI 経由のみ、 安全に開放可能。

#### ビジネスフロー残ロジック(B1 / B2)

- **B1 Manufacturing 完成品 INBOUND 失敗時の補償フロー** — `WorkOrder.complete` 後の `inventory.work_order.completed.v1` Saga で完成品 INBOUND が失敗するケースを補償。 `WorkOrderCompletionFailedEvent` を `manufacturing.completion.failed.v1` トピックへ Outbox 経由で発行する `EmitWorkOrderCompletionFailedService`(`REQUIRES_NEW`)を `inventory-core` に追加し、 `WorkOrderCompletedListener` が補償経路を起動。 manufacturing 側 `CompletionFailedListener` + `HandleCompletionFailureService` は WorkOrder 状態を巻き戻さず audit + warn のみ(完成品の物理的不可逆性)。 部品消費失敗時のキャンセルとは非対称(ADR-0017 follow-up)。 コミット [`963a289`](https://github.com/pero3dev/20260504/commit/963a289)
- **B2 Workflow SLA timeout(中央タイマ)** — ADR-0015 B2 の中央タイマ実体。 各 `WorkflowDefinition.instanceSla()` を見て `now - startedAt > sla` の STARTED インスタンスを 30 秒間隔で FAILED に強制遷移させ `workflow.instance.completed.v1` を発行する。 `WorkflowInstance.expireIfOverdue` で集約内遷移 + イベント発行、 `ExpireOverdueWorkflowsService` は per-instance 独立 TX(`TransactionTemplate`、 self-invocation の `@Transactional` 不発を回避)で 1 件失敗が他に波及しない。 `WorkflowSlaScheduler` は `@ConditionalOnProperty(platform.workflow.sla.enabled)` で test/loadtest profile では無効化可能。 `ApprovalFlow` は 24h SLA。 イベント駆動の Workflow 自動 step handler は別タスク(per-event-source カスタムロジック規模)として保留。 コミット [`43fc896`](https://github.com/pero3dev/20260504/commit/43fc896)

#### 監査ハードニング Phase 2(A4、ADR-0008 follow-up)

- **A4 audit-service S3 Object Lock 投入** — ADR-0008 の "未実装" 4 項目(S3 投入 / Merkle root S3 二重保管 / Athena / 1 年保持)を解消。 `AuditArchiveExporter` out port + AWS SDK v2 `S3Client` ベースの `S3AuditArchiveExporter` adapter を追加し、 日次 `ComputeDailyMerkleAnchorService` が anchor 計算後に同 tick で records(JSON Lines + gzip)+ anchor(JSON 単発)を S3 PUT する経路を完成。 `platform.audit.archive.enabled=true` で Bean 化、 default は無効(test/loadtest 環境で credential 無しに動く)。 export 失敗は warn ログのみで DB anchor は保持。 partition は `tenant=<id>/date=<yyyy-MM-dd>/` で Athena projection 形式に揃え、 `infra/audit-s3/{bucket-config, glue}` に Object Lock(Compliance mode + 365 days)+ Glue External Table DDL(records / anchors)+ AWS CLI ベースのデプロイランブック(Step 1〜10)を整備。 format は ADR で Parquet 想定だったが、 transitive 依存(hadoop-common 等)を避けるため MVP は JSON Lines に変更し ADR-0008 に追記。

#### コードギャップ解消(A1 / A2 / A3 / A5)

- **A2 Notification SES 実 sender** — `EmailSender` port は据え置きで `EmailSenderConfiguration` が `notification.email.provider={logging|ses}` を `@ConditionalOnProperty` で切替。 `SesEmailSender`(AWS SDK v2)で本番送信、 `SesException` を `EmailDeliveryException` に包んで Kafka 側 retry / DLQ に委ねる。 default は logging で従来挙動維持。 SMTP / Slack / SendGrid 等は同パターンで `@ConditionalOnProperty` を増やすだけで拡張可能。 コミット [`8de6c34`](https://github.com/pero3dev/20260504/commit/8de6c34)
- **A5 identity-broker テナント lifecycle 管理** — Pool 方式の `tenants` テーブル(V2 migration、 pattern check + status check)+ Tenant 集約 + `TenantManagementService`(Register / Deactivate / Get / List)+ OpenAPI schema-first(`AdminTenantsApi`、 4 endpoints)+ `TenantAdminController`。 `TenantNotFoundException`(404)/ `TenantAlreadyExistsException`(409)を `BusinessException` 派生にして commons-error が RFC 7807 ProblemDetail に自動変換。 Bridge 系 schema 作成 + Cognito group + 連携 secret は `infra/tenant-provisioning/README.md` に AWS CLI ベースの Step 1〜5 + シェルスクリプト雛形 + production セキュリティ要件 + Future Work で整理。 MVP は SecurityConfig が permitAll で admin API も無認証(production deployment では SUPER_ADMIN role 必須化を別途実装)。 コミット [`17c0693`](https://github.com/pero3dev/20260504/commit/17c0693)
- **A1 Workflow 承認アクション自動 step advance** — ApprovalFlow に対する Kafka 駆動の自動 step 進行。 `workflow.approval.action.v1` を購読し、 `ApprovalAction.{APPROVE, REJECT, SKIP}` を既存 `AdvanceWorkflowUseCase` に dispatch する `HandleApprovalActionService`。 listener は manual_immediate ack で `BusinessException`(既終端 / NotFound)は ack 続行、 RuntimeException は ack せず Kafka retry に委ねる。 これで ADR-0015「業態横断承認」の orchestration が REST に依らず Kafka 駆動で完結。 EDI ACK 待ち / WorkOrder ライフサイクル等の他 trigger は同パターンで listener を増やすだけで拡張可能(汎用 step trigger フレームワーク化は後フェーズ)。 コミット [`9657bb5`](https://github.com/pero3dev/20260504/commit/9657bb5)
- **A3 integration-hub S3Destination 第 1 弾** — 既存 `OutboundDestination` 抽象に `default writeBatch` を追加し、 S3 のような pay-per-PUT 系で 1 注文 = 1 オブジェクトにまとめられるように。 `S3Destination`(AWS SDK v2)+ `HubProperties.AdapterConfig.type={local|s3}` の切替 + `HubConfiguration.DestinationFactory` で adapter 名 → destination を構築。 key 形式は `<prefix>/<tenantId>/<yyyy-MM-dd>/<epoch-millis>-<rand>.csv` で per-tenant 日次 partition、 取引先 export / 監査人検証を容易にする。 SFTP / AS2-EDI / 外部 EC は同パターンで後続 commit。 コミット [`1578b2d`](https://github.com/pero3dev/20260504/commit/1578b2d)

#### Frontend monorepo 立ち上げ(F1 + F3)

- **F1 frontend/ 立ち上げ + Retail/EC vertical spike** — 13 サービス backend に対する Frontend 層(4 BFF + 4 Web UI、 memory:アーキテクチャ A2-15)を `frontend/` 配下に **Turborepo + pnpm workspace** で着工。 第 1 vertical として Retail/EC を実装:
    - `apps/bff-retail-ec` — Apollo Server v4(Fastify integration)+ schema-first(`.graphql`)+ DataLoader 雛形(SKU 在庫の N+1 防止、 in-memory mock、 F6 で実 backend HTTP 接続)+ 1 query(`sku(skuId)`)+ Vitest
    - `apps/web-retail-ec` — Vite + React 19 + Tailwind CSS + shadcn/ui semantic CSS 変数 + TanStack Router + TanStack Query + graphql-request、 `/dashboard` page で SKU 在庫テーブル表示、 stub auth(localStorage、 F2 で oidc-client-ts 実配線)+ Vitest + jsdom
    - `packages/shared` — 共通型(Tenant / InventorySnapshot)+ RFC 7807 ProblemDetail 判定 helper
    - 横断: 厳格 `tsconfig.base.json` + ESLint Flat config + Prettier + `.npmrc` + Turborepo task graph(`build` / `dev` / `lint` / `typecheck` / `test` / `format`)+ `.github/workflows/frontend.yml`(pnpm 9 / Node 20、 paths-trigger で Java CI と独立)
- **F3 残 3 業態 BFF + Web 複製 + CI 統合** — Manufacturing / 3PL / Wholesale を retail-ec パターンで一気に複製し、 全業態 UI スケルトンを揃える:
    - `apps/bff-{manufacturing,tpl,wholesale}` — 各 BFF が独自 schema を持つ:Manufacturing は `workOrder(workOrderId)` + WorkOrderStatus enum、 3PL は `stockMovement(movementId)` + MovementDirection enum、 Wholesale は `salesOrder(salesOrderId)` + SalesOrderStatus enum + 取引先別契約金額。 各々別 port(4002 / 4003 / 4004)で並走可能、 F6 で実 backend(`services/manufacturing` / `services/tpl` / `services/wholesale`)に繋ぐ
    - `apps/web-{manufacturing,tpl,wholesale}` — それぞれ port 5174 / 5175 / 5176 で起動し対応 BFF に proxy。 dashboard page は per-business のドメイン語彙(WorkOrder / StockMovement / SalesOrder)で表示、 stub auth は per-business localStorage key で隔離
    - CI 整理: `frontend.yml` の per-app step + `continue-on-error` を **`turbo run typecheck/lint/build/test` に統合**。 全業態で緑化したため soft-fail を解除

### Changed

- **`inventory.reservation.failed.v1` → `retail.reservation.failed.v1` 改名**(L4、ADR-0016 follow-up)。Phase 2 で命名規則が固まる前に作った共通名前空間を、業態別命名に揃える。コミット [`3cc68f3`](https://github.com/pero3dev/20260504/commit/3cc68f3)
- **`HandleReservationFailureService` を `cancelAfterReservationFailure()` 呼出に切替**(ADR-0018)。Reserve 失敗の補償経由で release イベントが発行されると Inventory Core が `InsufficientReserved` で失敗する事故を回避。
- **CI workflow `mvn verify` を `mvn -T 1C verify` に変更**(コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1))。

### Fixed

- **OutboxPublisher の self-invocation で `@Transactional` が効かなかった問題**を修正。`search_path` が tenant スキーマに切り替わらず、Bridge 方式マルチテナンシーが破綻していた。コミット [`5089e58`](https://github.com/pero3dev/20260504/commit/5089e58)
- **e2e-tests の HikariPool 競合**(多重 Spring Context 同居)を解消(コミット [`7a3b39e`](https://github.com/pero3dev/20260504/commit/7a3b39e))。 後に ADR-0014 で「e2e-tests は CI 外しに、KafkaIntegrationE2ETest を canonical に」と方針確定(コミット [`cc73e82`](https://github.com/pero3dev/20260504/commit/cc73e82))。
- **MyBatis `<constructor>` の javaType エイリアスを primitive に修正**(コミット [`696d225`](https://github.com/pero3dev/20260504/commit/696d225))。
- **Maven core プラグインを明示ピン**(コミット [`acea224`](https://github.com/pero3dev/20260504/commit/acea224))。Maven バージョン差異で未リリース版を参照する事故を回避。
- **`@SpringBootTest(classes=...)` 明示時にネスト `@TestConfiguration` が自動検出されない問題**を修正(コミット [`baaad32`](https://github.com/pero3dev/20260504/commit/baaad32))。
- **CI `ci.yml` の YAML parse 失敗**を修正(コミット [`00c50c5`](https://github.com/pero3dev/20260504/commit/00c50c5))。

### Repository Statistics

- **Modules**: 26(commons 10 + services 13 + 親 + e2e + commons-bom)
- **業態 / 共通基盤**: 4/4 + 9/9 = 13/13 services
- **Saga 配線**: 4/4 業態(Manufacturing 完成品 INBOUND 失敗補償含む)
- **Workflow**: SLA 中央タイマ + ApprovalFlow の Kafka 駆動 step advance(workflow.approval.action.v1 → APPROVE/REJECT/SKIP)
- **Notification 実 sender**: provider 切替で logging(default)/ ses(本番)
- **Tenant lifecycle**: identity-broker に Pool 方式の tenants テーブル + 4 admin REST + provisioning runbook
- **Integration Hub**: 1 adapter(retail-order-csv)+ destination type 切替(local / s3)— SFTP / AS2 / 外部 EC は同パターンで後続
- **Audit WORM 二重保管**: DB(WORM トリガ)+ S3 Object Lock(Compliance + 365 days)+ Athena projection
- **Frontend**: `frontend/`(Turborepo + pnpm workspace)+ 全 4 業態 vertical(`bff-{retail-ec,manufacturing,tpl,wholesale}` + `web-{retail-ec,manufacturing,tpl,wholesale}`)+ `packages/shared` + `frontend.yml` CI(turbo run typecheck/lint/build/test)
- **ADR**: 21 本(ADR-0021 で Pact Broker 本番ホスティングを EKS self-host on Aurora-C に確定)
- **E2E IT**: 8 ケース(`KafkaIntegrationE2ETest`)
- **Contract Test**: 5 経路 / 4 業態(Pact、Consumer + Provider verify Folder + Provider verify Broker) + Broker publish + can-i-deploy + verify result publish back + matchingRules 完備 + LambdaDsl + branch-aware selectors(ADR-0019 Phase 3 / 3.5 / 4 / 4.5 / 5 完了)

### Future Work

#### A. インフラ実展開 — Pact Broker(ADR-0021 Phase 1〜3 manifests 適用)

- Aurora-C 内 `pact_broker` DB の切り出し(`infra/pact-broker/db/001-...sql`)
- IRSA Role + ExternalSecretsOperator マニフェスト(`prod/pact-broker/*` secret 同期)
- ALB Cognito 用 User Pool App Client 作成(identity-broker と連携)
- ArgoCD Application apply + Route53 internal alias 登録
- GitHub Actions secret 投入(`PACT_BROKER_URL` / `_USERNAME` / `_PASSWORD`)→ `pact-broker.yml` workflow が dormant 解除
- Phase 3 社内告知(README の告知テンプレを流用)
- 同様に audit-service の S3 bucket 作成(`infra/audit-s3/README.md` の Step 1〜10)+ tenant 初期 onboarding(`infra/tenant-provisioning/README.md` の Step 1〜5)

#### B. ビジネスフロー(残ロジック)

- Workflow 自動 step handler の汎用化 — A1 で ApprovalFlow specific の listener を 1 つ実装した。 EDI ACK 待ち / WorkOrder ライフサイクルなど他 definition への展開は同パターンで listener を追加するだけで対応可能だが、 多 definition × 多 step trigger になると個別 listener が肥大化する。 `WorkflowDefinition` に `stepTriggers: Map<stepNo, EventTrigger>` を持たせて中央 listener が generic dispatch する方式への進化が次フェーズ
- Notification 他 channel(SMTP / Slack webhook / SMS / SendGrid)— A2 で SES + provider 切替の枠は出来たので、 同パターンで `@ConditionalOnProperty` を増やすだけ

#### C. 監査・コンプライアンス(ADR-0008 follow-up 残)

- format を MVP の JSON Lines + gzip から **Parquet 化**(scan cost 圧縮、 hadoop-common transitive を許容するか別 export pipeline に切り出すか要決定)
- 既存 anchor 日付の records 再 export 用の運用ジョブ(現状 export 失敗時は手動 S3 PUT が必要)

#### D. 外部連携(Integration Hub)

- A3 で S3Destination は導入済。 残: **SFTP**(Apache Mina sshd で test、 JSch or commons-vfs2 で実装)/ **AS2-EDI**(Apache Camel + camel-as2)/ **外部 EC**(Shopify / 楽天 / Amazon API、 各々別 listener)/ **EDIFACT**(Smooks)/ **distribution-BMS**(in-house)

#### E. テナント運用補完(A5 follow-up)

- `/v1/admin/**` を JWT 必須 + SUPER_ADMIN role に絞る SecurityConfig 拡張(MVP は permitAll)
- Bridge 系 schema 自動 provisioning Job(K8s CronJob で identity-broker の `tenants` テーブル → 各 Bridge DB の schema 整合性チェック + 不足分の自動作成)
- `SelectTenantService` で DEACTIVATED tenant の弾き出し
- user 管理 admin API(現状 SQL 直接、 next phase で REST 化)

#### F. テスト基盤

- 多重 Spring Context IT(`e2e-tests/`)の CI 復帰(技術スタック進化次第、 ADR-0014)
- Nightly E2E — 単一 context IT + Pact + 結合動作の三層 testing 構想の最終層(ADR-0014 Future complement)
- LocalStack による S3 / SES の integration test 整備(現状は Mockito mock のみ)

#### F2〜F7. Frontend follow-up(F1 + F3 から先)

- **F2** Cognito SAML 実配線 + Identity Broker token 交換 + BFF 側 JWT verify(jwks 取得 + verify + tenant 取出 + downstream への pass-through)
- **F4** `packages/ui/` に共通 design system(Button / Form / Table / Pagination / Toast 等)を切り出し。 shadcn/ui を base に owned-code 化(現状は各 web app が tailwind config と CSS 変数を独自に持つ重複あり)
- **F5** Storybook(per `packages/ui` + per app)+ Playwright E2E(BFF mock + 認証 flow)
- **F6** BFF resolver を本物の backend(`inventory-read-model` / `inventory-core` / 業態 service / `master-data`)に繋ぐ。 GraphQL Code Generator で typed client 生成、 `__generated__/` を消費
- **F7** **ADR-0022** で Frontend 構造を明文化。 i18n(react-intl or i18next)、 a11y(WCAG 2.1 AA)、 design token、 form validation(zod + react-hook-form)、 chart(recharts or visx)等の方針を決める
- CI: `pnpm-lock.yaml` を `frontend/` に commit して `--frozen-lockfile` に切替え(現状は CI で都度 install で生成)

#### G. 本番デプロイ(コードでは閉じない、 別 PR / 別リポジトリで管理)

- AWS landing zone(Org / Control Tower / アカウント分離 prod-stg-dev)
- VPC + subnet + NAT + SG + Route53 internal zone
- EKS prod クラスタ + Karpenter + ArgoCD + ExternalSecretsOperator
- Aurora 3 クラスタ(hot path / business / common)+ Flyway migration job
- ElastiCache(Redis Cluster)+ MSK + AWS Glue Schema Registry
- ALB + WAF + ACM + Cognito User Pool prod
- IAM / IRSA Role per service
- Datadog APM / logs / infra
- Argo Rollouts + Unleash + Pact Broker 実展開
- Load test(11.6k TPS / 1M SKU / 10K user)+ Pen-test + DR 計画 + SLO/Alert/runbook
- Frontend(4 BFF + 4 Web UI、 別 monorepo)
