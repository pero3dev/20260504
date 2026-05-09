# Changelog

このリポジトリの主要な変更履歴。書式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に準拠し、本プロジェクトは [Semantic Versioning](https://semver.org/lang/ja/) を採用予定(現在は `0.0.1-SNAPSHOT` 単一バージョン)。

詳細な設計判断は `docs/adr/` の各 ADR を、各機能の意図はコミットメッセージを参照。

---

## [Unreleased]

### Highlights

13 サービス(共通基盤 9 + 業態 4)のスキャフォールディング、業態 → Inventory Core の Saga 連結 4 経路 + Manufacturing 完成品 INBOUND 失敗時補償、業態 OUTBOUND/Cancel フローの完成、Workflow SLA 中央タイマ + 承認アクション自動 step advance、J-SOX 監査(WORM + ハッシュチェーン + Merkle anchor + S3 Object Lock 二重保管)実装、Pact 契約テスト Phase 5 + ADR-0021 で本番ホスティング決定、 Notification SES 実 sender、 identity-broker テナント lifecycle 管理、 integration-hub S3Destination、 Frontend monorepo(Turborepo + 全 4 業態 BFF + Web スケルトン + CI 緑化)+ 全 4 業態 BFF が実 service REST に接続(F6 phase 1 + 2)までを完了。13/13 サービス + 21 ADR + 8 E2E テストケース + Frontend 全 4 業態(BFF + Web 各 4、 mock 完全解消)。

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

#### ADR(22 本)

| # | タイトル | 状態 |
|---|---|---|
| 0001-0013 | プロジェクト基本方針 | Accepted(本セッション以前) |
| [0014](docs/adr/0014-cross-service-e2e-deferred-to-local-only.md) | Cross-service E2E deferred to local-only | Accepted |
| [0015](docs/adr/0015-saga-choreography-as-default-orchestration-on-demand.md) | Saga choreography as default | Accepted |
| [0016](docs/adr/0016-per-business-context-compensation-topics.md) | Per-business-context compensation topics | Accepted |
| [0017](docs/adr/0017-reserve-vs-reserve-ship-selection.md) | Reserve vs Reserve+Ship 使い分け | Accepted |
| [0018](docs/adr/0018-cancel-vs-cancel-after-reservation-failure.md) | cancel メソッド使い分け | Accepted |
| [0019](docs/adr/0019-pact-consumer-driven-contract-testing.md) | Pact Consumer-driven contract testing | Accepted |
| [0020](docs/adr/0020-ci-parallelization-strategy.md) | CI parallelization strategy | Accepted |
| [0021](docs/adr/0021-pact-broker-production-hosting.md) | Pact Broker 本番ホスティング | Accepted |
| [0022](docs/adr/0022-frontend-structure-and-libraries.md) | Frontend structure and libraries(F7) | Accepted |

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
- **F6 第 1 弾 bff-retail-ec を inventory-read-model に実接続** — mock を解消し、 UI が実 Redis 投影の在庫を見るようにする:
    - `apps/bff-retail-ec/src/clients/inventory-read-model-client.ts` 新規 — `GET /v1/inventories/{inventoryId}` を `fetch` で呼出し、 404 → null / 5xx → Error の最小限の error mapping。 `INVENTORY_READ_MODEL_URL` env で切替(default `http://localhost:8080`、 production は K8s Service DNS)
    - schema を `sku(skuId)` から **`inventory(inventoryId)`** に変更(inventory-read-model API と signature を揃える、 SKU 横断索引は read-model 側に secondary index API を入れて F6 phase 2 で再設計)
    - DataLoader は同パターンで `inventoryById` に統合、 1 リクエスト 1 client で同 id の重複呼出を抑制
    - JWT pass-through:resolver context が `Authorization: Bearer ...` を BFF → backend へそのまま流す(BFF 側 verify は F2 で追加)
    - test:client 単体(200 / 404 / 500)+ resolver の Mock client 経由 1 系統。 vi.stubGlobal で fetch を差替え
    - `apps/web-retail-ec` を新 schema に合わせ、 dashboard で固定 `inventoryId=1` を取りに行く構成に。 「該当無し」 / 「BFF 取得失敗」 をそれぞれ表示分岐
    - 残 3 業態(Manufacturing / 3PL / Wholesale)は同パターンで後続 commit。 各々の REST(`services/{manufacturing,tpl,wholesale}`)を確認しながら順次
- **F6 第 2 弾 残 3 業態 BFF を実 backend に接続** — F6 phase 1 と同 pattern で Manufacturing / 3PL / Wholesale を mock 解消し、全業態 BFF が実 service REST に繋がった状態に揃える:
    - `apps/bff-manufacturing/src/clients/manufacturing-client.ts` 新規 — `GET /v1/work-orders/{workOrderId}` を `fetch`、 404→null / 5xx→Error。 `MANUFACTURING_URL` env(default `http://localhost:8088`)。 schema は `WorkOrder { id, code, productSkuCode, locationId, plannedQuantity, status: WorkOrderStatus, plannedStartDate(scalar Date), version }` に置換、 enum を `PLANNED|RELEASED|COMPLETED|CANCELLED`(backend OpenAPI と一致)に揃える。 web-manufacturing dashboard も新 schema へ
    - `apps/bff-tpl/src/clients/tpl-client.ts` 新規 — `GET /v1/stock-movements/{movementId}`(`TPL_URL` default `http://localhost:8086`)。 schema は `StockMovement { id, code, partnerCode, skuCode, locationId, movementType: MovementType(INBOUND|OUTBOUND|ADJUSTMENT), quantity, status: MovementStatus(PLANNED|RECEIVED|DISPATCHED|CANCELLED), referenceCode, version }`。 phase 1 で曖昧だった `direction` を OpenAPI と整合する `movementType` + 状態機械(`status`)に明確化。 web-tpl dashboard も追従
    - `apps/bff-wholesale/src/clients/wholesale-client.ts` 新規 — `GET /v1/sales-orders/{orderId}`(`WHOLESALE_URL` default `http://localhost:8087`)。 schema は `SalesOrder { id, code, partnerCode, status: SalesOrderStatus(PLACED|SHIPPED|CANCELLED), currency, totalAmount(Float), requestedDeliveryDate(scalar Date), items: [SalesOrderLine { skuCode, locationId, quantity, unitPrice }], version }`。 backend で server resolved の `unitPrice`(取引先別契約価格)が UI まで透過。 web-wholesale dashboard は明細 list を表示
    - 各 BFF とも DataLoader は `xxxById` に集約し 1 リクエスト 1 client、 JWT は `Authorization: Bearer ...` を BFF→backend へ pass-through(F2 で BFF 側 verify を追加予定)
    - test:各 client 単体(200 / 404 / 500、 `vi.stubGlobal` で fetch 差替)+ resolver Mock client 経由(`vi.spyOn(client, 'getXxx')`)。 phase 1 で確立した pattern を 3 業態に一気に展開
    - これにより全 4 業態 BFF が mock 完全解消、 F2(Cognito SAML 実配線)+ F7(ADR-0022 Frontend 構造文書化)に進む準備完了
- **F2 phase A BFF 側 JWT verify**(Cognito 配線前の足場)— 4 BFF 全て が Identity Broker 発行 JWT を verify するように:
    - `packages/shared/src/auth/verify-jwt.ts` 新規 — `jose@5` の `createRemoteJWKSet` + `jwtVerify` を使った `createJwtVerifier({ jwksUrl, issuer, audience? })`。 RS256 固定、 `clockTolerance` default 30s、 JWKS は in-process キャッシュで cold start 1 回のみ identity-broker へ HTTP。 Identity Broker `NimbusJwtTokenIssuer` の access token claim と一致する `BffUserClaims { userId, tenantId, roles[], scopes:{locations,partners}, mfaStrength }` に正規化。 `token_use=access` 強制(session token を弾く)/ `tenant_id` 必須 / `sub` を Number 化
    - `packages/shared/src/auth/bff-context.ts` 新規 — `buildBffAuth({ authorizationHeader, verifier })` で `Bearer ` 抜き出し + verify 呼出を 4 BFF で重複させない最小 helper。 verifier 未設定なら token を抽出だけして user=null(dev 用)、 verify 失敗は `JwtVerificationError` を throw
    - 各 BFF index.ts は `JWT_JWKS_URL` + `JWT_ISSUER`(任意で `JWT_AUDIENCE`)env 設定で verifier を構築。 設定無しは warn を吐いて pass-through 維持(local dev 用)、 設定有りで verify 失敗は GraphQLError(`code=UNAUTHENTICATED, http.status=401`)で reject
    - `BffContext` に `user: BffUserClaims | null` を追加。 resolver は `context.user?.tenantId` 等を直接読める。 backend へは元 token を pass-through なので双方で同 claim を共有
    - test:`generateKeyPair('RS256')` でローカル鍵生成 → JWKS を `vi.stubGlobal('fetch')` で配って verify を 1 系統エンドツーエンド検証(正常 / aud 不一致 / 期限切れ / iss 不一致 / token_use=session / tenant_id 欠落)。 `buildBffAuth` 単体は 5 系統(token 有無 × verifier 有無の組合せ + verify 失敗の throw 伝播)
    - F2 残: web 側 `oidc-client-ts` 配線(login redirect / callback / token refresh / logout)+ Cognito SAML pool の CDK / Identity Broker の token exchange は phase B 以降
- **F2 phase B web 側 OIDC 配線**(Cognito 接続前の web app 足場)— 4 web app 全てが `oidc-client-ts` を介した login / callback / logout flow を持つように:
    - `packages/shared/src/web-auth/auth-manager.ts` 新規(subpath `@inventory/shared/web-auth` で export、 BFF 側から見えないよう分離)— `AuthManager interface { getAccessToken / isAuthenticated / signIn / signOut / handleCallback }` と factory `createAuthManager(config, devStorageKey)`。 `OidcAuthManager`(oidc-client-ts UserManager wrapper、 `response_type=code` PKCE) と `DevAuthManager`(sessionStorage に dummy `dev-token-...` を入れるだけ) の 2 実装を出し分け
    - `readOidcConfigFromEnv()` で `VITE_OIDC_AUTHORITY` / `VITE_OIDC_CLIENT_ID` / `VITE_OIDC_REDIRECT_URI` を Vite `import.meta.env` から読む。 全項目揃えば OIDC、 1 つでも欠ければ dev fallback。 任意項目は `VITE_OIDC_POST_LOGOUT_REDIRECT_URI` / `VITE_OIDC_SCOPE`(default `openid profile`)
    - `packages/ui/src/components/auth-buttons.tsx` + `oidc-callback-page.tsx` 新規 — Login/Logout button 切替と `/callback` page を共通化(各 web app が `<AuthButtons authManager={..} />` と `<OidcCallbackPage authManager={..} onSuccess={..} />` を貼るだけ)。 4 web app から RootLayout の boilerplate ~25 行 × 4 を削減
    - 各 web app `lib/auth.ts` を全面置換(stub `localStorage` → `createAuthManager(...)`)、 `router.tsx` に `/callback` route と `<AuthButtons />` を追加。 `graphql-client.ts` は `getAuthToken()` 経由で UserManager の access_token を JWT pass-through(F2 phase A の BFF verify と組合さって prod では署名検証が走る)
    - test:`@inventory/shared/web-auth` 単体(env パース 4 系統 + dev fallback 4 系統)、 各 web app `auth.test.ts`(initial 状態 + signIn/signOut round-trip)
    - F2 残(phase C): Cognito User Pool + SAML federation の CDK 化、 Identity Broker `POST /v1/auth/exchange`(Cognito access token → IB session/access token、 tenant 解決込み)、 BFF の K8s ConfigMap で `JWT_JWKS_URL` を配布
- **F2 phase C federation 配線**(SAML フェデレーション全体)— Cognito SAML を経由した社内 IdP login → Identity Broker exchange → BFF verify までの end-to-end pipeline を成立させる:
    - **Identity Broker `POST /v1/auth/exchange` 新設**(OpenAPI / `AuthController` / `ExchangeFederatedTokenService` / `IdpTokenVerifier` port + `NimbusIdpTokenVerifier` 実装)— 外部 IdP の access token を JWKS で verify(`platform.identity.federation.{issuer-uri,jwks-uri,subject-claim,audience,audience-claim}` 設定経由)し、 subject claim(default `email`)で内部 User を引いて IB session token + accessibleTenants[] を返す。 既存 `POST /v1/auth/sessions` と同 response 型に揃え、 web app は次の `/v1/auth/tenant-sessions` を同じ flow で叩ける。 列挙攻撃対策で token 不正 / subject 未 provision / 内部 User 不在は全て `AuthenticationFailedException`(401)に丸める
    - test:`ExchangeFederatedTokenServiceTest` 4 系統(正常 + verify 失敗 + 内部 User 不在 + subject 形式違反)、 `IdpTokenVerifier` は port mock で網羅
    - **web 側 silent token refresh** — `OidcAuthManager` に `automaticSilentRenew=true` + UserManager events(`userLoaded` / `userUnloaded` / `accessTokenExpired` / `silentRenewError`) wiring。 `OidcConfig.silentRedirectUri` 任意項目を追加し、 Vite `VITE_OIDC_SILENT_REDIRECT_URI` env で `/silent-renew.html` を hosting する想定。 prod は accessTokenExpiringNotificationTimeInSeconds の default 60 秒前に hidden iframe で refresh、 失敗時は cache クリアで UI が再 login を促す
    - **infra scaffolding** — `infra/cognito/README.md` に Cognito User Pool + SAML IdP 連携の AWS CLI ランブック(User Pool 作成 → Hosted UI domain → SAML IdP 登録 → App Client → SAML IdP 側 ACS URL 設定 → 配布値)。 `infra/k8s/identity-broker/federation-configmap.yaml`(`FEDERATION_*` env を ConfigMap + Secret で注入)、 `infra/k8s/bff/jwt-configmap.yaml`(`JWT_ISSUER` + `JWT_JWKS_URL` を 4 BFF 共通 ConfigMap で配布)、 `infra/k8s/bff/deployment-snippet.yaml`(`envFrom` 注入の BFF Deployment 雛形)
    - F2 残: SAML JIT provisioning(SCIM 連携 / 別 batch で先行 user 投入)、 Cognito の Terraform / CDK IaC 化、 K8s helm/kustomize overlay は別タスクで切出し
- **F2 phase D silent-renew.html を 4 web app に hosting**(F2 phase C で `OidcConfig.silentRedirectUri` 受口は出来ていたが iframe が読みに行く実体 page が存在せず token expire ごとに 404 → silent renew 失敗していた状態を解消):
    - **`@inventory/shared/web-auth` に `runSilentRenewCallback(env)` helper 追加**(env から OidcConfig 読み直し → UserManager 構築 → `signinSilentCallback()` で URL から auth code を取り parent window へ postMessage)。 OIDC env 不在(dev fallback)は no-op、 callback 失敗は `console.warn` で飲んで parent 側 `silentRenewError` event に任せる。 unit test 2 ケース(env 不在 / env 有るが code 無し)
    - **4 web app に `silent-renew.html` + `src/silent-renew.ts` + `vite.config.ts` rollup multi-page input 追加**(retail-ec / manufacturing / tpl / wholesale すべて同型)。 `silent-renew.html` は `<meta robots>` で indexer 排除 + 単一 module script tag のみの空ページ。 `silent-renew.ts` は helper を `import.meta.env` 渡しで一発呼ぶだけ。 build 時に Vite が `dist/silent-renew.html` を生成し prod hosting で `VITE_OIDC_SILENT_REDIRECT_URI=https://app.example/silent-renew.html` を指せる
    - effect: token expire 60 秒前(oidc-client-ts default)に hidden iframe が `silent-renew.html` を load → そこの bundled script が auth server から新 access token を fetch → parent UserManager に postMessage で返す → `userLoaded` event 経由で `OidcAuthManager.cachedAccessToken` が更新。 ユーザは login 状態のまま長時間滞在可能、 失敗時のみ再 login が必要
    - F2 残: SAML JIT provisioning / Cognito Terraform-CDK IaC 化(silent-renew.html 整備で web 側 OIDC は完成、 残りは IdP 側のみ)
- **F2 phase E SAML JIT(Just-In-Time)provisioning(Identity Broker)**(F2 phase C で外部 IdP 認証は通すが内部 User 不在 → 401 だった経路を、 default tenant + role で auto-provision するオプションに置換):
    - **`platform.identity.federation.jit.{enabled, default-tenant-id, default-role}` 設定 + `FederationJitProperties` record + `FederationConfig` Bean**(default は `enabled=false` で従来挙動を維持、 列挙攻撃対策と整合)。 `FEDERATION_JIT_*` env で K8s ConfigMap から ON 切替
    - **`UserRepository.save(User)` + `TenantMembershipRepository.add(TenantMembership)` を port + impl + Mapper に追加**(従来は参照のみだった repo に書込みを開放)。 users テーブルは User の Snowflake id を caller(service)が採番、 tenant_memberships は独自 PK `id` を `TenantMembershipRepositoryImpl` が `SnowflakeIdGenerator` で採番(domain TenantMembership は id を持たない設計を維持、 PK 採番は永続化詳細として隠蔽)
    - **`ExchangeFederatedTokenService` を JIT 対応に拡張**(`@Transactional` を method に付与、 SnowflakeIdGenerator + TenantRepository + FederationJitProperties を constructor 追加注入)。 unknown user 時の `jitProvision()` 分岐:
        - JIT 無効 → `AuthenticationFailedException`(従来挙動)
        - JIT 有効 + default-tenant-id 未設定 → 401(設定不備、 server log に warn)
        - default tenant が DB 不在 → 401(warn)
        - default tenant が DEACTIVATED → 401(warn)
        - すべて clear → Snowflake user id 採番 + `User.create(id, email, PasswordHash("$external_federation$"), email-local-part)` で User INSERT + `TenantMembership(userId, tenantId, displayName, locale, [defaultRole], [], [])` で membership INSERT
    - **password_hash sentinel `$external_federation$`**:BCrypt 形式ではないため password 認証 endpoint からは決して通らない、 SAML 経路専用ユーザを明示する marker
    - **memberships 0 件防御**:既存ユーザでも accessibleTenants が空なら `AuthenticationFailedException`(過去 batch 不整合等で User 単独 row が残るケースを安全側に倒す)
    - **test**: `ExchangeFederatedTokenServiceTest` を JIT 対応に書き直し、 9 ケース(従来 4 + JIT 5)で網羅:happy / 検証失敗 / JIT 無効 user 不在 / subject not email / **JIT 有効で provision 成功 + ArgumentCaptor で User と Membership 内容を assert** / JIT 有効 default-tenant 未設定 / default-tenant DB 不在 / default-tenant DEACTIVATED / memberships 空
    - F2 残: Cognito User Pool / SAML IdP 設定の Terraform / CDK IaC 化(現在は `infra/cognito/README.md` に AWS CLI ランブックのみ)
- **F2 phase F Cognito User Pool + SAML Federation の Terraform IaC 化**(phase E までで Identity Broker 側 federation 実装は完成、 ここで AWS リソース定義を CLI ランブックから IaC SoR に格上げ):
    - **`infra/cognito/terraform/`** 新設、 7 ファイル(`versions.tf` / `variables.tf` / `main.tf` / `saml-idp.tf` / `app-clients.tf` / `outputs.tf` / `terraform.tfvars.example`)+ `README.md`。 `terraform 1.7+` / `aws provider ~> 5.70` で pin、 backend は S3 + DynamoDB lock の placeholder を `versions.tf` に置く(本タスクでは未有効化、 環境準備後に init)
    - **`aws_cognito_user_pool`**: `username_attributes=email` / `auto_verified_attributes=email` / 強い password policy(SAML 経由でも Cognito 必須なので 12 文字大小数字記号)/ `admin_create_user_config.allow_admin_create_user_only=true`(SCIM / 手動投入のみ)/ MFA OFF(SAML IdP 側で実施)/ recovery=verified_email
    - **`aws_cognito_user_pool_domain`**: Hosted UI 用ドメイン prefix を `^[a-z0-9-]{3,63}$` で validate
    - **`aws_cognito_identity_provider`**: SAML provider(provider_name=`CorporateSAML`、 metadata URL を変数で注入、 `attribute_mapping` は default に `email` / `given_name` / `family_name` / `custom:employee_id` を含める)
    - **`aws_cognito_user_pool_client` × 4**: `for_each = var.app_clients` で 4 業態(retail-ec / manufacturing / tpl / wholesale)に同型展開。 `supported_identity_providers=[CorporateSAML]` で SAML 経由のみに固定、 PKCE + `code` grant のみ、 SPA 想定で `generate_secret=false`、 access/id token=60 分 / refresh=30 日、 `prevent_user_existence_errors=ENABLED`(列挙攻撃対策)
    - **`outputs.tf`**: K8s ConfigMap / Secret に注入する 8 値(`issuer_uri` / `jwks_uri` / `app_client_ids[*]` / `saml_acs_url` / `saml_entity_id` / `hosted_ui_base_url` / `user_pool_id` / `user_pool_arn`)。 `saml_acs_url` と `saml_entity_id` は SAML IdP 管理画面に手動登録する値(IdP 側 IaC は phase 範囲外)
    - **`terraform.tfvars.example`**: env ごとに `terraform.<env>.tfvars` をコピー → `metadata_url` / callback URL を実値に置換 → `terraform plan -var-file=...` で適用するフロー
    - **親 `infra/cognito/README.md` 更新**:冒頭に `prod は ./terraform/ の IaC を使う` 注記追加、 構成図に `terraform/` を追加、 既知の制約から「IaC 化は後タスク」を削除、 JIT provisioning は phase E で実装済に書換
    - effect: prod 変更は全て `terraform plan` 経由で SecOps レビュー可能、 drift 検知は `terraform plan -detailed-exitcode` を CI で週次実行する想定(別タスクで wiring)。 AWS Console 直接編集は緊急時のみ
    - F2 残: SAML IdP 側(Azure AD / Okta / Google Workspace)の IaC 化 — IdP ごとに API / 管理画面が違うため Terraform 化は次フェーズ。 metadata URL / Reply URL / Entity ID / NameID format / attribute statement の手動登録が前提
- **F2 phase F follow-up Terraform CI workflow 追加**(phase F で書いた `.tf` を CI で構文 / 設定整合性検査するため、 `infra/**/*.tf` 変更時に自動 trigger):
    - **`.github/workflows/terraform.yml`** 新設、 2 job 構成:
        - `fmt-check`: `terraform fmt -check -recursive infra/` で全 .tf を整形チェック(差分があれば fail、 ローカルで `terraform fmt -recursive infra/` を当てて再 push する運用)
        - `validate-cognito`: `infra/cognito/terraform/` に cd → `terraform init -backend=false` → `terraform validate`(provider plugin の download だけで AWS credential 不要、 prod backend は触らない)
    - **trigger 条件**: `infra/**/*.tf` / `infra/**/*.tfvars*` / 本 workflow 自身の変更で main 向け PR + main push に発火。 Java / Frontend CI とは独立(`paths-ignore` と相互排他)
    - **`hashicorp/setup-terraform@v3`** で terraform 1.7.5 を install(`terraform_wrapper: false` で素の CLI を使い、 後段 step で stdout を直接見られる)
    - **将来追加候補(本 workflow に bolt-on)**: `tflint`(命名 / 未使用 resource / deprecated 検知)/ `terraform plan -detailed-exitcode` による週次 drift 検知(read-only AWS credential + 0 以外で Slack 通知)/ `checkov` / `tfsec`(SecOps 静的解析)
    - **モジュール追加時の手順**: `validate-<module>` job を `validate-cognito` と同型で複製。 数が増えたら matrix 化(現状 1 モジュールなので直書き)
- **A5 follow-up⁹ `writePathsAreAuditable` を master-data / notification / workflow / tpl の 4 サービスに一括展開**(共通基盤系を ArchUnit 強制下に揃え、 8/13 サービス到達。 残り 5 = inventory-core / retail-ec / wholesale / manufacturing / integration-hub の業態 + write 権威の 5 で来 phase):
    - **master-data**: `CreateSku/Location/PartnerService` 3 つは既に `@Auditable` 付与済、 `Get` 系 3 つは read-only。 注釈追加なしで opt-in
    - **tpl**: `PlanStockMovementService` は既に `@Auditable`、 `GetStockMovementService` は read-only。 注釈追加なしで opt-in
    - **notification**: `NotifyOnInventoryMovementService` の `repository.append` が rule 対象。 元 `inventory.movement.v1` event は inventory-core 側で audit 済の Kafka projection のため `@AuditExempt(reason=...)` 付与。 `pom.xml` に `commons-audit` 依存追加
    - **workflow**: `Start/Advance/HandleApprovalActionService` は既に `@Auditable`、 `ExpireOverdueWorkflowsService` は scheduler 起動の SLA 超過 housekeeping (ユーザ操作ではない) のため `@AuditExempt(reason=...)` 付与
    - **opt-in 進捗**: identity-broker (⁵) / audit-service (⁷) / inventory-read-model (⁸) / analytics (⁸) / master-data (⁹) / notification (⁹) / workflow (⁹) / tpl (⁹) = **8 / 13**。 残 5 = inventory-core / retail-ec / wholesale / manufacturing / integration-hub
- **A5 follow-up⁸ `writePathsAreAuditable` を inventory-read-model + analytics に展開**(follow-up⁵ pilot → ⁷ audit-service の流れで、 projection 系 service を 2 つ追加で opt-in。 全 13 サービスのうち 4 service が ArchUnit 強制下に入った):
    - **inventory-read-model**: `InventoryProjectionStore` を経由するため (Repository ではない) 対象 0 件で vacuously 合格。 `GetInventoryService` は既に `@Auditable(read=true)` 付与済、 `ApplyInventoryMovementService` は projection 自身で元イベントは inventory-core 側で audit 済。 注釈追加なしで opt-in
    - **analytics**: `IngestOrderPlacedService` の `processedRepo.markProcessed` / `summaryRepo.incrementOrder` が rule の write パターン (`mark[A-Z].*` / `increment[A-Z].*`) に合致するため `@AuditExempt(reason=...)` 付与(Kafka projection、 元 order event は発生源 service で audit 済 / 二重カウント回避)。 `GetDailyOrderSummariesService` は read-only で対象外
    - **analytics `pom.xml`** に `commons-audit` 依存を追加(consumer 側だが `@AuditExempt` マーカが必要)
    - **opt-in 進捗**: identity-broker (⁵) / audit-service (⁷) / inventory-read-model (⁸) / analytics (⁸) = 4 / 13。 残 9 サービスは inventory-core / master-data / notification / workflow / retail-ec / tpl / wholesale / manufacturing / integration-hub
- **A5 follow-up⁷ `@AuditExempt` マーカ導入で `writePathsAreAuditable` を audit-service にも opt-in**(follow-up⁵ で identity-broker pilot だった ArchUnit ルールを次の service に展開する第 1 弾。 audit emitter 自身は audit すると自己再帰になるため、 専用の exempt マーカを reason 必須で設計):
    - **`commons-audit/AuditExempt`** 新設(`@Target(METHOD)` / `@Retention(RUNTIME)` / `String reason()` 必須)。 ランタイム挙動には影響せず(`AuditableAspect` は見ない)、 ArchUnit ルール用の compliance マーカ
    - **`HexagonalLayerRules.writePathsAreAuditable`** を更新:`@Auditable` または `@AuditExempt` のいずれかが付いていれば合格(条件名と違反メッセージも更新)
    - **audit-service `ProcessAuditEventService` / `ComputeDailyMerkleAnchorService`** に `@AuditExempt(reason=...)` 付与(audit emitter 自身の自己再帰防止 / chain housekeeping は scheduler 責務)
    - **audit-service `pom.xml`** に `commons-audit` 依存を追加(consumer 側は今まで未依存だった)
    - **audit-service `ArchitectureTest`** に `@ArchTest writePathsAreAuditable` opt-in、 5/5 ArchUnit 合格
    - **rule sanity test を 4 ケースに拡張**(`ExemptService` fixture 追加、 `@AuditExempt` 単独でも合格することを実証)
    - **未着手 (本 phase スコープ外)**: inventory-read-model / analytics 等の Kafka projection 系。 同型展開は次 phase
- **A5 follow-up⁶ SUPER_ADMIN 初回 provisioning 経路を確立**(follow-up⁴ で `/v1/admin/**` を SUPER_ADMIN role 必須にした時点で発生する chicken-and-egg 問題を閉じる。 「初回の SUPER_ADMIN をどう生成するか」が後続の admin 業務全部の前提となるため、 ロックアウト不能性を含めて runbook 化):
    - **`V4__platform_tenant.sql`** で `platform` テナントを seed(`display_name="Platform Administration"`, `status=ACTIVE`, `locale=ja`, `ON CONFLICT DO NOTHING` で再起動冪等)
    - **`TenantManagementService.deactivate("platform")` を `TenantProtectedException` (409) で拒否**:platform tenant を消すと admin が完全にロックアウトされるため、 repository を引かずに早期 throw。 invariant 違反として info ログを残し ops から見えるように
    - **`TenantProtectedException`** 新設(`ERR_TENANT_PROTECTED` / 409 Conflict / `BusinessException` 継承で GlobalExceptionHandler が RFC 7807 化)
    - **`PLATFORM_TENANT_ID = "platform"`** を `TenantManagementService` の `public static final` 定数として公開。 将来 SelectTenantService や filter 等で参照する可能性に備えるが、 今は use case 内 1 箇所参照のみ
    - **OpenAPI 仕様**:`POST /v1/admin/tenants/{tenantId}/deactivate` の description に「予約テナント (`platform`) は 409」と記載 + `'409'` response を追加
    - **`infra/tenant-provisioning/README.md` に SUPER_ADMIN 初回 provisioning section** を追加(BCrypt hash 生成コマンド例 / `INSERT INTO users` + `INSERT INTO tenant_memberships` SQL 雛型 / 動作確認手順)。 federation 経路の SUPER_ADMIN provisioning は SAML group → role mapping 設計が必要なため別 phase
    - **`TenantManagementServiceTest` に新ケース** 追加(9 ケース):`deactivate("platform")` → `TenantProtectedException`、 repository は呼ばれない (`verifyNoInteractions` 相当)
- **A5 follow-up⁵ ADR-0008 J-SOX 補完策の ArchUnit ルール `writePathsAreAuditable` を実装、 identity-broker pilot で opt-in**(CLAUDE.md と `@Auditable` Javadoc が「ArchUnit による強制が必須」と明記しながら未実装だった compliance gap を閉じる。 全サービス一斉 opt-in は projection / Kafka consumer 系の `@AuditExempt` 設計が未確定のため、 まず identity-broker のみで動かす):
    - **`HexagonalLayerRules.writePathsAreAuditable()`** 追加(クラス単位 / opt-in)。 判定:`..application.usecase..` 配下クラスがリポジトリ書込(`*Repository.{save/update/delete/insert/append/add/remove/mark*/increment*/persist}`)を呼ぶなら、 少なくとも 1 メソッドに `@Auditable` を直接付与必須
    - **クラス単位粒度を採用**(method 単位ではない):各 use case クラスは概ね 1〜2 公開メソッドで全て同一 use case ポートの実装になっており、 私的ヘルパ(例:`ExchangeFederatedTokenService.jitProvision`)に書込を分離しても 公開側 `@Auditable` で AOP が動くため
    - **`@Auditable` 参照は FQN 文字列**(既存 `AuditMaskingRules` と同様):commons-test → commons-audit の compile 依存を増やさず、 ライブラリ利用側に余分な transitive dep を伝搬させない
    - **`allowEmptyShould(true)`**:write 呼出が 1 件も無い read-only service で対象 0 件になっても vacuously 合格させる
    - **rule 自体の sanity test** を 3 ケース新設(`WritePathsAreAuditableTest` + synthetic fixtures `FakeRepository` / `GoodAuditableService` / `BadNonAuditableService` / `ReadOnlyService`):違反検出 / 合格 / read-only vacuously 合格の 3 経路を実証
    - **identity-broker `ArchitectureTest` に opt-in**(`@ArchTest writePathsAreAuditable`)。 follow-up² / ³ で全 usecase が compliant 化済のため pilot として無修正で 5/5 ArchUnit 合格
    - **未着手 (本 phase スコープ外)**: 他 12 サービスの opt-in。 audit-service の `ProcessAuditEventService`(audit 自身を audit するの矛盾)/ inventory-read-model の `ApplyInventoryMovementService`(Kafka 投影)/ analytics の `IngestOrderPlacedService` 等 projection 系の `@AuditExempt` 設計が決まり次第、 順次 opt-in
- **A5 follow-up⁴ identity-broker `SecurityConfig` で `/v1/admin/**` を JWT + SUPER_ADMIN role 必須に絞り込み**(`infra/tenant-provisioning/README.md` のセキュリティ要件から繰上げ。 ここまで `/v1/admin/**` は MVP `permitAll` で誰でも叩け、 follow-up² で `@Auditable` を付けても認可ゲートが無いと記録だけ残って実害が止まらない問題を閉じる):
    - **`SecurityFilterChain` を 2 本に分割**:`adminFilterChain`(Order=HIGHEST_PRECEDENCE / `securityMatcher("/v1/admin/**")` / `PlatformSecurity.applyDefaults` で JWT 検証 + RFC 7807 認証認可エラー + `TenantContextFilter` を有効化 + `hasRole("SUPER_ADMIN")`)+ `publicFilterChain`(Order=LOWEST_PRECEDENCE / `anyRequest().permitAll()` / 認証/JWKS/actuator はそのまま叩ける)
    - **自己 `JwtDecoder` Bean** を identity-broker に追加。 `NimbusJwtDecoder(DefaultJWTProcessor + JWSVerificationKeySelector)` で in-process JWKSource を使い、 自分が発行した JWT を JWKS HTTP 経路 を 踏まずに 検証(他 12 サービスは jwk-set-uri で identity-broker に HTTP しに行くが、 identity-broker は自分で持っている)
    - **filter chain 分離の効果**:`/v1/auth/sessions` 等の認証エンドポイントは oauth2ResourceServer を 持たない publicFilterChain で処理されるため、 stale Bearer が付いた状態でも 401 漏れが起きず、 ログイン経路に副作用が及ばない
    - **`AdminSecurityTest` 新設**(`@WebMvcTest` + Spring Security test の `with(jwt())`):JWT 無し → 401 / SUPER_ADMIN 無し → 403 / SUPER_ADMIN 有り → 200 / userId path も同様に守られる、 4 ケース。 admin path に到達しない場合は use case が呼ばれないことも `verifyNoInteractions` で確認
    - **TenantAdminController / UserAdminController の Javadoc** から「MVP permitAll」の caveat を削除し、 「`adminFilterChain` で SUPER_ADMIN 必須」の現状に書換
    - **未着手 (本 phase スコープ外)**: SUPER_ADMIN role の provisioning 経路 web UI 化(現状はプラットフォーム管理用テナント membership に SUPER_ADMIN を含む roles で行を入れる運用 SQL)
- **A5 follow-up³ admin 向け user 参照 REST API (read-only MVP) 追加**(`infra/tenant-provisioning/README.md` の Future Work から繰上げ。 現状 user 一覧/単体取得は SQL 直接しか手段が無く、 admin operator が DB 不可視な環境でも操作できるよう REST 化。 write 系は password vs. federation-only provisioning の ADR 待ちで本 phase は read のみ):
    - **OpenAPI 仕様**: `GET /v1/admin/users`(全件)+ `GET /v1/admin/users/{userId}`(単体)+ `UserResource` schema 追加(`userId: int64` / `email` / `displayName` のみ。 password hash は admin にも露出させない)+ `admin-users` tag 追加
    - **port-in**: `GetUserUseCase`(`get(long)` + `listAll()`) と `UserNotFoundException`(`ERR_USER_NOT_FOUND` / 404)を新設。 既存 `GetTenantUseCase` と同型で `BusinessException` 継承により GlobalExceptionHandler が RFC 7807 化
    - **port-out**: `UserRepository.findAll()` 追加、 `UserMapper.findAll` SQL(`ORDER BY id`)を XML mapper に追加。 件数は SaaS 規模で低数千を想定し cursor pagination は将来課題として明記
    - **use case**: `UserManagementService implements GetUserUseCase` 新設。 全 read メソッドに `@Auditable(read = true)` 付与:`USER_GET`(targetIdExpression=`#userId`)/ `USER_LIST_ALL`(targetId 無し)。 admin の参照行為自体が J-SOX 統制対象のため read を漏らさない
    - **REST controller**: `UserAdminController implements AdminUsersApi` 新設。 `User` → `UserResource` 変換時に password hash を 明示的に 落とす(BCrypt は leak しても緩衝あるが、 設計上 admin に hash を見せる必要が無い)
    - **`UserManagementServiceTest` 新設**(3 ケース):get 該当無し → `UserNotFoundException` / get 正常 / listAll が repository を素通し
    - **`infra/tenant-provisioning/README.md` Future Work** から該当行を「read 系実装済 / write 系 ADR 待ち」に書換
- **A5 follow-up² `TenantManagementService` 全 4 メソッドに `@Auditable` 付与**(`infra/tenant-provisioning/README.md` の セキュリティ要件 から繰上げ。 J-SOX 上テナント追加 / 削除 / 参照は重要統制点で、 admin API 呼出を audit-service が全て記録する必要がある):
    - **write 系**: `register` → `action="TENANT_REGISTER"` + `targetIdExpression="#command.tenantId"`、 `deactivate` → `action="TENANT_DEACTIVATE"` + `targetIdExpression="#tenantId"`(`@Transactional` と並置)
    - **read 系も `read = true` で監査**: `get` → `action="TENANT_GET"` + `targetIdExpression="#tenantId"` + `read=true`、 `listAll` → `action="TENANT_LIST_ALL"` + `read=true`(targetId 無し)。 admin の参照行為自体が J-SOX 統制対象のため read を漏らさない
    - **AOP 経由なので unit test (`new TenantManagementService(...)` 直接 instantiate)では aspect 起動せず既存 8 ケースは無修正で全 pass**。 production runtime では Spring proxy 経由で `AuditableAspect` が `audit.log.v1` topic に発行
    - **`infra/tenant-provisioning/README.md` のセキュリティ要件**から該当行を「付与済」に書換
- **A5 follow-up `SelectTenantService` で DEACTIVATED tenant を弾き、 token 再発行を拒否**(`infra/tenant-provisioning/README.md` の Future Work から繰上げ。 tenant deactivation の意味的な幅を完成させる修正で、 deactivate 後でも既存 membership 経由で access token が新規発行できる security gap を閉じる):
    - **`SelectTenantService` constructor に `TenantRepository` を追加注入**、 selectTenant 内で membership 確認後に `tenantRepository.findById(tenantId)` を引いて `TenantStatus.DEACTIVATED` なら `TenantAccessDeniedException` で拒否
    - **tenant row 不在(membership 有るのに tenants テーブル不在)の data inconsistency** も `TenantAccessDeniedException` + warn ログで安全側に倒す
    - **既発行 access token は TTL 切れまで有効**(stateless JWT の制約)。 即時 revocation には別 mechanism(token 失効リスト / Redis 失効キャッシュ等)が必要、 本 phase では新規発行のみブロック
    - **`SelectTenantServiceTest` 新設(本 service には test が無かった、 6 ケース網羅)**:happy(ACTIVE)/ session token 不正 / membership 不在 / **tenant DEACTIVATED** / **tenant row 不在 / tenantId 形式違反**
    - **`infra/tenant-provisioning/README.md` の Future Work** から該当行を「実装済」に書換、 stateless JWT 制約のフォローアップを注記
- **F6 follow-up phase 2 GraphQL Codegen を残 3 業態(manufacturing / tpl / wholesale)に同型展開**(phase 1 で retail-ec pilot 緑化後、 同 pattern を mechanical 適用):
    - 各 web app に `@graphql-codegen/{cli, typescript, typescript-operations}` devDep + `pnpm codegen` script + `codegen.ts` 設定 + `src/lib/graphql-client.ts` の hand-written interface を生成型 import に置換
    - **schema 入力**: manufacturing → `bff-manufacturing/src/schema.graphql`(`Date` scalar マップ)、 tpl → `bff-tpl/src/schema.graphql`(scalar 不要)、 wholesale → `bff-wholesale/src/schema.graphql`(`Date` マップ)
    - **置換対象 interface**: `WorkOrderQueryResult` → `WorkOrderQuery`、 `StockMovementQueryResult` → `StockMovementQuery`、 `SalesOrderQueryResult` → `SalesOrderQuery`(+ 各 `ViewerQueryResult` → `ViewerQuery`)。 既存呼出側互換のため `type alias` は残す
    - **client.request<T, V> generics**: 全 fetch 関数で 2 引数指定に揃え、 Variables 型も schema 駆動に
    - effect: 4 業態すべてで GraphQL schema → TS 型の単一情報源化が完成。 BFF schema 変更時は CI codegen 段階で自動再生成され、 web app 側の使用箇所が型不整合を typecheck で検知
- **F6 follow-up phase 1 GraphQL Codegen 導入(retail-ec pilot)**(BFF schema 駆動の TS 型生成。 4 web app で hand-written interface(`InventoryQueryResult` 等)が BFF schema からドリフトするリスクを build 時 typecheck で潰す。 retail-ec 1 業態で pilot 検証 → CI 緑化を確認後、 残 3 業態に同型展開する 2 段構え):
    - **deps**: `@graphql-codegen/cli` ^5.0.3 + `@graphql-codegen/typescript` ^4.1.2 + `@graphql-codegen/typescript-operations` ^4.4.0 を retail-ec の devDependencies に追加(pilot 期間は他 web app は無変更)
    - **`apps/web-retail-ec/codegen.ts`** 新設(TS 設定ファイル。 schema=`../bff-retail-ec/src/schema.graphql` / documents=`src/lib/graphql-client.ts` の `gql\`\`` テンプレ / 出力 `src/__generated__/graphql.ts` / config: `skipTypename: true`(現行 interface 互換)+ `useTypeImports: true` + scalars `DateTime` → `string`)
    - **`pnpm codegen`** script を retail-ec に追加(`graphql-codegen --config codegen.ts`)
    - **`src/lib/graphql-client.ts` を生成型に refactor**:hand-written `InventoryQueryResult` / `ViewerQueryResult` interface を `import type { InventoryQuery, ... } from '../__generated__/graphql'` に置換、 `client.request<T, V>` の 2 引数 generics で `Variables` 型も schema 駆動に。 後方互換のため `export type InventoryQueryResult = InventoryQuery` の alias は残す
    - **turbo.json に `codegen` task 追加**:`inputs=[codegen.ts, src/lib/graphql-client.ts, ../bff-*/src/schema.graphql]` / `outputs=src/__generated__/**`。 `typecheck` / `lint` / `test` / `build` に `dependsOn: [codegen]` を追加し、 CI が turbo 経由で何を回しても codegen が事前に走るようにする(他 packages は codegen script を持たないので turbo が skip)
    - **`__generated__/`** は frontend `.gitignore` で既に対象。 commit せず CI 内 + 開発者 `pnpm codegen` で生成
    - **次フェーズ予定**: 残 3 web app(manufacturing / tpl / wholesale)の同型展開。 codegen.ts は schema パス + documents パスを書換えるだけで動く想定
- **F4 follow-up phase C Popover + Command + Combobox 追加**(phase A で建てた design system に autosuggest / search palette を追加、 SKU / 取引先 / location 選択など候補多数の入力 UI に使う):
    - **deps**: `@radix-ui/react-popover` ^1.1.4 + `cmdk` ^1.0.4 を `@inventory/ui` の dependencies に追加
    - **`Popover`**(`@radix-ui/react-popover` 背後): `Popover` / `PopoverTrigger` / `PopoverAnchor` / `PopoverContent` の compound API。 outside-click 閉 / ESC 閉 / focus 管理は Radix 担当、 アニメ classes は phase B の `tailwindcss-animate` 利用
    - **`Command`**(`cmdk` 背後): keyboard nav / fuzzy 部分一致 / 仮想スクロール対応の search palette。 `Command` / `CommandInput` / `CommandList` / `CommandEmpty` / `CommandGroup` / `CommandItem` / `CommandSeparator`。 単体でも検索 UI に使えるが、 主に Combobox の中身として機能
    - **`Combobox`**(Popover + Command 合成): `value`(string | null)/`onChange` の controlled API。 `items: ComboboxItem[]`(`value` / `label` (ReactNode) / `keywords` / `disabled`)で候補を渡す。 trigger は選択値 / placeholder を表示、 押下で popover 内に検索 input + 候補リスト展開、 上下キー / Enter / ESC 全対応、 同値 select で deselect、 `aria-label` + `aria-expanded` で screen reader 対応
    - **stories 3 件**:`Popover/Detail`(button → 詳細表示)/ `Command/SkuSearch`(SKU 5 件候補)/ `Combobox/SkuPicker`(controlled state、 disabled item 含む 5 候補)
    - **使い分けガイド(将来の README 化候補)**:
        - 候補 ≤ 7 件 + sort 不要 → `<Select>`(native 動作 + 単純)
        - 候補 8〜数百 件 + 検索したい → `<Combobox>`(本 phase 追加)
        - フリーテキスト追加 OK の autosuggest → 別 phase で `<Combobox>` に `creatable` prop を追加
- **F4 follow-up phase B Toast / Dialog / Select の animation 復元**(phase A で `tailwindcss-animate` plugin 未導入のため意図的に削除していた `data-[state=open]:animate-in` 等を、 plugin 配線後に復元):
    - **`tailwindcss-animate` ^1.0.7 を `@inventory/ui` の dependencies に追加**(本 plugin は `animate-in` / `animate-out` / `fade-in` / `fade-out` / `slide-in-from-right` / `zoom-in-95` などの utility を Tailwind に追加し、 Radix の `data-state="open|closed"` 遷移と組合せて宣言的に animation を書ける)
    - **`packages/ui/src/tailwind-preset.ts` の `plugins` に bundle**(各 web app は preset 継承で自動的に utility 利用可能)。 packages/ui 内の Storybook も同 preset を継承するため stories 上でも animation が動く
    - **Toast.Root**: 開く時 `slide-in-from-right`(右端 Viewport から流入)+ 閉じる時 `fade-out`、 default duration 5 秒の auto dismiss と組合せて UX を仕上げる
    - **DialogOverlay**: `fade-in` / `fade-out` で半透明 overlay の出現/消滅をふわっと
    - **DialogContent**: `fade-in` + `zoom-in-95` で modal が中央から滑らかに浮上、 閉じる時は `zoom-out-95` で消失
    - **SelectContent**: `fade-in` / `fade-out` で dropdown ぱっと/さっと(slide は不要、 trigger 直下から表示)
    - effect: 同じ Radix primitives + 同じ design system のまま視覚的な滑らかさのみ追加。 機能差分なし、 a11y(focus / aria)挙動も既存のまま
- **F7 phase 1 ADR-0022 skeleton install**(ADR で確定したライブラリの実装下地のみ、 既存 web app への組込みは phase 2 別 PR)— `@inventory/shared/i18n` と `@inventory/ui` 拡張を一気に投入:
    - **deps**: `i18next` ^23 + `react-i18next` ^15(shared)、 `zod` ^3 + `react-hook-form` ^7 + `@hookform/resolvers` ^3 + `recharts` ^2 + `react-error-boundary` ^4(ui peer)
    - **`@inventory/shared/i18n` subpath 新設** — `createI18n({ language?, fallbackLanguage?, resources? })` factory(react-i18next plugin 注入、 `defaultNS=common`、 fallback `ja`、 escapeValue=false)。 `defaultResources`(ja/en × common 1 NS)+ `mergeResources(...sources)` helper(言語 union + namespace 後勝ち merge)。 catalog は JSON ファイル(`locales/{ja,en}/common.json`、 `auth` / `common` / `error` 3 セクション)で TS は `with { type: 'json' }` import attributes(TS 5.3+ / Vite 6+ 対応)
    - **`@inventory/ui` 拡張** — `<Form>` + `<FormField>` (react-hook-form `FormProvider` + `Controller` + `<p role="alert">` で a11y 対応 error 表示) / `<DefaultErrorFallback>` (`react-error-boundary` の `FallbackProps` 受取り、 `aria-live="assertive"` で screen reader 通知 + 再試行 button)。 `@inventory/ui/charts` subpath で `<LineChart>`(recharts wrapper、 `ResponsiveContainer` + CSS 変数色で design system と整合、 凡例 / Tooltip 標準スタイル)
    - **shadcn semantic CSS 変数に `--destructive` / `--destructive-foreground` 追加**(form error 表示で必要)、 tailwind preset の colors にも反映
    - test:`createI18n` 5 系統(ja/en 切替、 fallback、 changeLanguage、 未知 key)+ `mergeResources` 2 系統
    - phase 2 候補: 既存 4 web app の dashboard を i18n 化(`<I18nextProvider>` + `useTranslation` で `auth.login` 等を JSX に注入)+ recharts で在庫推移 chart 1 つ追加 + a11y lint(`eslint-plugin-jsx-a11y` + `@axe-core/react` dev mode)
- **F7 phase 2 dashboard 文言を i18n + 1 chart + a11y lint 第 1 層**(phase 1 で揃えた skeleton を 4 web app に貼り込む):
    - 4 業態の追加 catalog(`shared/src/i18n/locales/{ja,en}/{retail-ec,manufacturing,tpl,wholesale}.json`)を追加し、 `app-resources.ts` で `retailEcResources` / `manufacturingResources` / `tplResources` / `wholesaleResources` を re-export。 `common.json` の section 名 `common` → `ui` に rename(namespace 名と被って `t('common.loading')` が紛らわしいため、 `t('ui.loading')` に統一)
    - `createI18n` を resources から namespace 自動抽出するよう改修(`extractNamespaces`、 `common` 先頭で残りはアルファベット順)。 各 web app は `useTranslation('retail-ec')` 等で業態 namespace を直接参照できる
    - 4 web app の `main.tsx` で `<I18nextProvider i18n={i18n}>` + top-level `await createI18n({ language: 'ja', resources: mergeResources(defaultResources, ...) })`(MVP は `ja` 固定、 phase 3 で Identity Broker `tenant.locale` claim から切替予定)
    - 4 web app の `router.tsx` の dashboard 文言(タイトル / 説明 / loading / error / フィールド label)を `useTranslation` 経由に変換。 `t('dashboard.fetch_failed', { message })` 等 interpolation も投入
    - `web-retail-ec` に `<LineChart>`(`@inventory/ui/charts`)で「直近 7 日の在庫推移(デモ)」を追加。 mock データだが ResponsiveContainer + 凡例 + Tooltip + CSS 変数色の wiring を vertical 確認
    - **a11y lint 第 1 層**: root `frontend/package.json` に `eslint-plugin-jsx-a11y` ^6.10 を追加、 `eslint.config.mjs` に `.tsx` 限定で `jsx-a11y` plugin + `recommended` ruleset を適用。 既存 / 新規 JSX 全てが lint pass する状態で commit
    - phase 3 候補: `tenant.locale` claim → `language` の動的切替 / `@axe-core/react` dev mode 投入 / 残 3 業態 dashboard へ chart 拡張 / Storybook(F5)で a11y lint 第 3 層
- **F7 phase 3 form vertical + a11y 第 2 層**(phase 1 で揃えた `<Form>` / `<FormField>` + zod を実 UI に貼り込み、 a11y を runtime でも見張る):
    - **`web-retail-ec` dashboard に Filter form**:`inventoryId` を入力 → submit で `useQuery` の queryKey 更新 → fetch。 `<Form>` + `<FormField>`(`@inventory/ui`)+ `react-hook-form` + `zodResolver(zod schema)` の vertical demo。 schema は `t()` で error message を i18n 化(`required` / `numeric` パターン違反)。 input は `inputMode="numeric"` で a11y キーボード補助
    - retail-ec catalog に `dashboard.filter.{title, inventory_id_label, inventory_id_description, fetch_button, validation.{required, numeric}}` を ja/en 追加
    - **`@inventory/shared/dev` subpath 新設** + `startAxeDevScanner({ intervalMs?, initialDelayMs? })`(`axe-core` を dynamic import で polling scan、 default 5s 間隔、 違反は `console.warn` に流す)。 `@axe-core/react` 4.x が React 19(`react-dom.findDOMNode` 削除)非対応のため、 axe-core 単体 + polling で代替。 `@axe-core/react` の React 19 対応版が出たら本ヘルパは差替予定
    - **4 web app の main.tsx に `if (import.meta.env.DEV) void startAxeDevScanner()`**(production tree shake で axe-core は bundle に含まれない)。 a11y 4 層の第 2 層が稼働
    - phase 4 候補: `tenant.locale` claim → language 動的切替 / 残 3 業態 dashboard も filter form / chart 化 / F5 Storybook + addon-a11y(第 3 層)
- **F7 phase 4 残 3 業態 dashboard を filter form + chart 拡張**(retail-ec パターンを 4 業態揃え、 form / chart の vertical を全業態で完成):
    - **`web-manufacturing`**: WorkOrder ID 入力 → submit、 mock 完成数 7 日 LineChart
    - **`web-tpl`**: Movement ID 入力 → submit、 mock 入庫 / 出庫数量 7 日 LineChart(2 series)
    - **`web-wholesale`**: Order ID 入力 → submit、 mock 受注額 7 日 LineChart
    - 各 web app の package.json に `@hookform/resolvers` / `react-hook-form` / `zod` / `recharts` / `react-error-boundary` を direct dep として追加(transitive 経由では pnpm が型解決に出さないため)
    - 各業態 catalog(ja/en)に `dashboard.filter.*` + `dashboard.trend.*` を追加。 schema は render 内で `t()` 経由 error message を i18n 化、 input は `inputMode="numeric"` で a11y キーボード補助
    - phase 5 候補: `tenant.locale` claim → language 動的切替 / Form の submit-pending state 表示 / chart に Tooltip カスタマイズ / F5 Storybook + addon-a11y(第 3 層)
- **F7 phase 5a `tenant.locale` claim 配線(IB → BFF)**(ADR-0022 の「言語切替はテナント単位固定」を実体化、 web 側適用は phase 5b で別 PR):
    - **Identity Broker `tenants.locale` カラム + V3 migration**(BCP47 風 `^[a-z]{2}(-[A-Z]{2})?$`、 default `ja`)。 `tenant_memberships.tenant_locale` を `tenant_display_name` と同様 denormalize し JWT 発行 hot path で 1 row fetch で取れる構造に
    - **`Tenant` 集約に `locale` フィールド + validation**、 register / restore は default `ja` で旧 API 互換、 新 API で locale 明示可
    - **`TenantMembership` record の 7 引数化**(`tenantLocale` 追加)。 旧 6 引数 constructor を互換のため残し既存 test は無修正で通る
    - **TenantRow / TenantMembershipRow / TenantMapper.xml / TenantMembershipMapper.xml** を locale carry に対応(SELECT/INSERT/UPDATE すべて locale を含む形)、 Repository 実装の domain ↔ row 変換を locale 対応
    - **`NimbusJwtTokenIssuer.issueAccessToken`** の claim に `locale` を追加(`membership.tenantLocale()` 由来)
    - **`@inventory/shared/web-auth/verify-jwt.ts`** の `BffUserClaims` に `locale: string`(default `ja` fallback)、 `mapClaimsOrThrow` で抽出
    - test: 既存 IB 20 tests / shared 単体は無修正で通過、 verify-jwt 単体に「locale 明示」「locale 欠落 → ja fallback」の 2 系統を追加。 bff-context sample claim も locale 補完
    - phase 5b 候補: web 側で `BffUserClaims.locale` を取って `i18n.changeLanguage(locale)` を呼ぶ(BFF に GraphQL `Query.viewer { locale, tenantId, roles }` を追加し、 web app 起動時 fetch + apply)
- **F7 phase 5b `tenant.locale` claim を web に適用(BFF Query.viewer + i18n 動的切替)**(phase 5a で配線した locale を実体的にユーザ体験に反映、 ADR-0022 「言語切替はテナント単位固定」を完成):
    - **4 BFF schema(retail-ec / manufacturing / tpl / wholesale)に `Viewer` type + `viewer: Viewer` query 追加**(`userId` / `tenantId` / `roles` / `locale` / `locations` / `partners`)。 token 検証済み `BffContext.user` (= `BffUserClaims`) を resolver で `Viewer` 形に詰め替えるだけで Backend 通信 0、 GraphQL クライアントが welcome / locale 適用に再利用できる
    - **4 BFF resolver に `Query.viewer` 追加 + `toViewer(claims)` helper**(未認証時は null 返却)、 `resolvers.test.ts` に「context.user 有り → Viewer 返却」「未認証 → null」の 2 ケース追加
    - **`@inventory/shared/i18n` に `useApplyTenantLocale(locale)` hook 新設**(react hook、 `i18n.language === locale` なら no-op、 値が変わった時だけ `i18n.changeLanguage()` を起動)。 export を `index.ts` に追加し各 web app から subpath import 可能に
    - **4 web app の `lib/graphql-client.ts` に `VIEWER_QUERY` + `fetchViewer()` + `ViewerQueryResult` 型追加**(既存 `fetchInventory` / `fetchWorkOrder` / `fetchStockMovement` / `fetchSalesOrder` と同じ `graphql-request` パターン、 `Authorization: Bearer ...` も既存 middleware で自動付与)
    - **4 web app `RootLayout` を viewer fetch + apply に拡張**:`useQuery({ queryKey: ['viewer'], queryFn: fetchViewer, staleTime: Infinity, retry: false })` で初回 fetch、 `useApplyTenantLocale(viewerData?.viewer?.locale)` で `tenant.locale` を i18n に反映。 未認証(viewer = null)は initial language(= `ja`)のまま据え置きで認証前から UI 文言が日本語表示されつづける
    - effect: テナント A の locale = `en` ならログイン後に各画面が英語に切替、 テナント B の locale = `ja` ならそのまま日本語。 ユーザ操作不要、 IB の `Tenant.locale` 変更だけで全業態 web app 一斉切替が成立
    - phase 5 候補(残): Form の submit-pending state 表示 / chart Tooltip カスタマイズ / F5 Storybook + addon-a11y(第 3 層)/ packages/ui の component 拡充(Pagination / Toast / Dialog / Select)/ F2 残(SAML JIT provisioning / Cognito Terraform-CDK / silent-renew.html)
- **F7 phase 5c Form submit-pending state + LineChart Tooltip 単位整形**(phase 5 候補のうち polish 2 件を一括投入、 design system の Form / chart wrapper の API 完成度を上げる):
    - **`@inventory/ui` に `SubmitButton` 追加**(`useFormState().isSubmitting` を購読、 送信中は `disabled + aria-busy + pendingLabel` に切替、 外部 `pending` prop で `useQuery.isFetching` 等を OR merge 可能)。 これまで 4 dashboard で `<button type="submit">…` をベタ書きしていた箇所を一掃し、 Spinner / 二重 submit 防止 / a11y 属性が一箇所に集約
    - **`@inventory/ui/charts/LineChart` に `valueFormatter` + `labelFormatter` prop 追加**(Tooltip / YAxis tick の整形 callback、 default は `Number.toLocaleString()`)。 series 名と data point も渡るので「currency / 数量 / 件数」など unit を寄せられる。 `LineChartValueFormatter<TPoint>` 型も export
    - **i18next の number interpolation を活用した unit ラベル**を 4 業態 catalog に追加(`dashboard.trend.value_unit`):retail-ec/tpl は `{{value, number}} 個` (en: `pcs`)、 manufacturing は `{{value, number}} 件` (en: `orders`)、 wholesale は `¥{{value, number}}` (両言語)。 `i18n` インスタンスの `Intl.NumberFormat` で locale-aware に 1,234,567 と桁区切りされる
    - **4 web app dashboard の置換**: `<button type="submit">` → `<SubmitButton label pendingLabel pending={isFetching} />`、 `LineChart` に `valueFormatter={(value) => t('dashboard.trend.value_unit', { value })}` を配線
    - **Catalog**: 4 業態 × 2 言語 = 8 ファイルに `dashboard.filter.fetch_button_pending` + `dashboard.trend.value_unit` を追加
    - effect: `取得` ボタン押下中は `取得中...` 表示で disable、 chart hover 時 Tooltip が `1,234 個` / `¥1,200,000` のように単位付き表示。 `tenant.locale` が `en` なら `Fetching...` / `1,234 pcs` に自動切替で phase 5b の i18n 切替と整合
    - phase 5 候補(残): F5 Storybook + addon-a11y(第 3 層)/ packages/ui の component 拡充(Pagination / Toast / Dialog / Select)/ F2 残(SAML JIT provisioning / Cognito Terraform-CDK / silent-renew.html)
- **F4 follow-up `packages/ui` component 拡充(Pagination / Toast / Dialog / Select)**(F4 で骨組みを切り出した design system に、 4 web app が今後必要とする building block を投入。 Radix UI primitives ベースで a11y を default 確保):
    - **`Pagination`**(zero-dep): cursor-based prev/next ボタン + page info(ADR の REST 規約 = page number 持たない)。 `hasPrev` / `hasNext` / `onPrev` / `onNext` / `pageInfo` / `isPending` / `prevLabel` / `nextLabel` / `ariaLabel` を受ける presentational component。 cursor 値は親が `useState` 等で管理
    - **`Toast` + `useToast` hook**(`@radix-ui/react-toast` 背後): app root に `<ToastProvider>` を 1 つ mount すれば、 任意の component から `const { toast } = useToast(); toast({ title, description, variant })` で発火可能。 variant は `default` / `success` / `error`、 default 5 秒 auto dismiss、 swipe / × ボタン / ESC で手動 dismiss、 `aria-live` (Radix が status role 付与)で screen reader 対応
    - **`Dialog`**(`@radix-ui/react-dialog` 背後): shadcn 標準の compound API(`Dialog` / `DialogTrigger` / `DialogContent` / `DialogTitle` / `DialogDescription` / `DialogFooter` / `DialogClose`)。 focus trap / ESC close / outside-click close / aria-modal は Radix が担保
    - **`Select`**(`@radix-ui/react-select` 背後): native `<select>` ではなくカスタム dropdown だが、 listbox role / 矢印キー nav / type-ahead / scroll はすべて Radix。 `Select` / `SelectTrigger` / `SelectValue` / `SelectContent` / `SelectItem` / `SelectLabel` / `SelectGroup` / `SelectSeparator`
    - **deps**: `@radix-ui/react-{dialog,select,toast}` を `@inventory/ui` の dependencies に追加(peer ではなく direct dep:Radix は web app に直接 import される ことが少ないので transitive 経由で十分)
    - **4 web app `main.tsx` を `<ToastProvider>` ラップ**(`<QueryClientProvider>` 内側に配置で React tree の他 provider と同居)
    - **smoke 接続(retail-ec のみ)**: dashboard で fetch 完了時に `useToast` を呼び、 成功 / 失敗 / 該当なし を i18n 化された通知として表示。 catalog ja/en に `dashboard.toast.{fetch_success_title, fetch_success_description, fetch_failed_title, not_found_title}` 追加。 残 3 業態への接続は実際の mutation が出てきた phase で配線
    - **animation class は意図的に省略**: 当初 `data-[state=open]:animate-in fade-in` 等を入れたが `tailwindcss-animate` plugin が preset に未導入で機能しないため削除。 必要になった時 plugin 追加 + class 復元の予定。 機能としては Radix の即時 mount / unmount で動作
    - 残作業候補: `<Combobox>`(autosuggest 必要になったら)/ Dialog から SubmitButton + Form 連動の confirm dialog ヘルパー / Toast に countdown progress bar
- **F5 Storybook 8 + addon-a11y(a11y 第 3 層)導入**(ADR-0022 の a11y 4 層防御の第 3 層を実体化、 design system の docs / 視覚 regression / a11y check の足場):
    - **packages/ui に Storybook 8.4 + Vite 6 ビルダ install**(`@storybook/react-vite` + `@storybook/addon-essentials` + `@storybook/addon-a11y` + `@storybook/addon-interactions` + `@storybook/test`)。 設定は `.storybook/main.ts`(framework / stories glob / addons)+ `.storybook/preview.ts`(global parameters: a11y addon 全 story 有効、 backgrounds、 controls)
    - **packages/ui 単独で Tailwind を回せる体制**: `packages/ui/tailwind.config.ts`(F4 で切り出した `tailwind-preset` を継承、 content scope は本パッケージの src)+ `packages/ui/postcss.config.js`(tailwindcss + autoprefixer)。 `preview.ts` から `'../src/styles.css'` を import して全 stories に CSS 変数 / Tailwind utility が効く
    - **既存 9 component 全てに smoke stories 追加**:
        - `Button`(primary / secondary / ghost / disabled の 4 variant)
        - `Pagination`(Middle / FirstPage / LastPage / Pending の 4 状態)
        - `Toast`(success / error / default / 永続 toast の 4 variant、 ToastProvider decorator で発火デモ)
        - `Dialog`(削除確認 dialog のフルフロー、 Trigger → Content → Footer → Close)
        - `Select`(controlled、 4 ステータス選択肢、 value 表示で双方向 binding 確認)
        - `Form` + `FormField` + `SubmitButton`(zod schema + react-hook-form + Sync / AsyncWithPending 2 ケース、 pending 時 SubmitButton が `保存中...` に切替)
        - `DataTable`(InventoryList / Empty 2 ケース、 generic 解消用 wrapper 経由)
        - `LineChart`(Default 2 series + WithUnitFormatter で valueFormatter `{{value}} 個` を確認、 600px wrapper decorator)
        - `DefaultErrorFallback`(本物 Error object を渡してメッセージ表示確認)
    - **a11y 第 3 層が稼働**: addon-a11y は axe-core を全 stories に対して実行し、 違反は Stories panel に表示。 jsx-a11y(第 1 層、 lint 時)+ axe-core dev mode polling(第 2 層、 web app 起動時)+ Storybook addon-a11y(第 3 層、 component 単位で隔離)+ 手動 checklist(第 4 層、 release 前)で ADR-0022 の 4 層防御が完成
    - **generic component の Meta 推論回避**: `LineChart<TPoint>` / `DataTable<T>` は generic なため `Meta<typeof Component>` で argTypes が `unknown` に潰れる。 specialized wrapper(`LineChartStory(props: LineChartProps<TrendPoint>)` / `DataTableInventory(props: ...)`)経由で型推論を維持する pattern を確立
    - **CI への影響**: stories は `src/**/*.stories.tsx` で main tsconfig の include 配下に入るため `pnpm typecheck` で型検査される(CI で stories の型エラーも検出)。 `build-storybook` は別 script で turbo `build` task に未連結(必要になった時 `^build` 含めて連結)
    - **dependencies / animation の制約**: `tailwindcss-animate` は preset 未導入のため Toast / Dialog / Select の `data-[state=open]:animate-in` 等は phase A で削除済。 plugin 導入 + class 復元は一括で別 PR
    - 残作業候補: web app 単位の Storybook(dashboard 全体を story 化)/ chromatic 等の visual regression / addon-interactions で submit flow E2E テスト / `tailwindcss-animate` plugin 導入 + Toast/Dialog/Select の animation 復元
- **F7 ADR-0022 Frontend 構造とライブラリ選定**(50+ engineers の規模で各 web app の分裂を防ぐ)— i18n / a11y / form / chart / state / error boundary / runtime config の 7 領域を確定:
    - **i18n**: `react-i18next`(`i18next` + `react-i18next` + JSON catalog、 namespace = 業態 + common、 フォーマットは `Intl` native、 言語切替はテナント単位固定 = `tenant.locale` claim 由来、 fallback `ja`)。 react-intl(ICU MessageFormat)は書き味と community 活性度で却下
    - **a11y**: WCAG 2.1 AA 目標 + 4 層防御(`eslint-plugin-jsx-a11y` + `@axe-core/react` dev mode + Storybook `addon-a11y`(F5)+ manual checklist)。 shadcn/ui = Radix primitives ベースで a11y デフォルト無料
    - **form**: `zod` schema + `react-hook-form` + `@hookform/resolvers/zod`。 controlled/uncontrolled 両対応で再 render 最小化、 GraphQL Codegen 型 + zod schema を併存(wire format vs UI バリデーション)
    - **chart**: `recharts`(主、 declarative、 React 19 対応、 95% カバー)+ `visx`(逃げ道、 個別 chart のみ)。 nivo / Apache ECharts は React wrapper / 19 対応 lag で却下。 大規模 time-series は recharts Canvas → visx + Canvas へ段階的に逃がす
    - **client state**: server state は TanStack Query、 form は react-hook-form、 router は TanStack Router search params、 local UI は React 標準 `useState`/`useReducer`。 **Redux/Zustand/Jotai は不採用**(global undo / cross-tab sync 等の要件が顕在化したら別 ADR)
    - **error boundary**: `react-error-boundary` + 3 層(route / suspense+query / app root)。 `@inventory/ui/<DefaultErrorFallback>` を提供
    - **runtime config**: build-time `VITE_*` env で完結(env ごとに別 image build)。 K8s ConfigMap → window.\_\_ENV\_\_ injection は採らない(SSR でないため意味が小さい)。 緊急 toggle は Unleash で runtime fetch
    - 実装 phase は 4 段階(skeleton install / 既存 dashboard を i18n + 1 chart 追加 / form を順次 react-hook-form+zod へ統一 / Storybook 導入)で別 PR 切出し

- **F4 共通 design system 切出し(`packages/ui`)** — 4 web app の Tailwind 設定 / CSS 変数 / Header の重複を解消し、 共通 component を 1 パッケージに集約:
    - `packages/ui/src/lib/cn.ts` — shadcn 標準の className マージ helper(`clsx + twMerge`)
    - `packages/ui/src/components/{button,app-shell,data-table}.tsx` — Button(primary / secondary / ghost)、 AppShell(brand + nav の構造化、 TanStack Router `Link` 内蔵)、 DataTable(generic な table)
    - `packages/ui/src/styles.css` — Tailwind directives + shadcn semantic CSS 変数(`--background` / `--primary` / `--muted` / `--border` / `--radius`)
    - `packages/ui/src/tailwind-preset.ts` — 色 token を CSS 変数経由で `theme.extend.colors` に揃える preset
    - 各 web app:`tailwind.config.ts` を `presets: [preset]` で簡潔化、 `index.css` を削除して `main.tsx` から `@inventory/ui/styles.css` を import、 `router.tsx` の独自 Header を AppShell で置換、 `web-retail-ec` は SKU 在庫表を DataTable で書き直し
    - 重複削減: 4 web app から `index.css`(計 24 行 × 4)+ tailwind config の color/border-radius 定義(計 18 行 × 4)+ Header JSX(計 17 行 × 4)が消え、 後続 F5(Storybook)で `packages/ui` 単体を doc 化できる足場が出来た

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
- **Frontend**: `frontend/`(Turborepo + pnpm workspace)+ 全 4 業態 vertical(`bff-{retail-ec,manufacturing,tpl,wholesale}` + `web-{retail-ec,manufacturing,tpl,wholesale}`)+ `packages/{shared,ui}`(共通 design system: AppShell / Button / DataTable + tailwind preset + shadcn CSS 変数)+ `frontend.yml` CI(turbo run typecheck/lint/build/test)
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

- **F2 残 follow-up**(phase A〜C で BFF verify / web OIDC 配線 / IB exchange / K8s scaffolding 完了)— SAML JIT provisioning(SCIM or 先行 user 投入 batch)、 Cognito を Terraform / CDK で IaC 化、 K8s manifest を helm / kustomize で env overlay 化、 silent renew iframe 用 `silent-renew.html` の Vite public asset 化
- **F4 follow-up** `packages/ui` の component 拡充(Form / Pagination / Toast / Dialog / Select 等)+ dark mode CSS 変数 + size variant
- **F5** Storybook(per `packages/ui` + per app)+ Playwright E2E(BFF mock + 認証 flow)
- **F6 follow-up**(残 3 業態 BFF の実 backend 接続は完了)— GraphQL Code Generator で typed client 生成 + `__generated__/` を消費する経路に進化、 inventory-read-model 側に SKU 横断 index API を追加して BFF schema に `inventories(skuId)` リスト query を復活、 加えて pagination(cursor)/ filter / sort の BFF DSL 設計
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
