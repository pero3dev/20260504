# ADR-0021: Pact Broker は EKS self-host(Aurora-C 同居)で運用する

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: Architecture, Platform Team

## Context

ADR-0019 で Pact Consumer-driven contract testing を Phase 5(consumer version selectors の本格運用)まで完走させ、 Pact Broker のインフラと CI 連携も整った。 Phase 3 で `infra/pact-broker/docker-compose.pact-broker.yml` に local 用 Broker を提供した時点で、 ADR-0019 は **本番ホスティング先確定は別 ADR** と記録していた。 本 ADR でその決定を行う。

現状:

- 5 経路 / 4 業態の Consumer-Provider 契約が稼働中(Phase 2.2 完了)
- Provider verify が Folder source(reactor 内)と Broker source(`PACT_BROKER_URL` env でゲート)の 2 経路を持つ(Phase 3.5)
- CI workflow `pact-broker.yml` は **`PACT_BROKER_URL` secret が未設定なら全 job skip** で main に置けてある(Phase 3〜5 すべて dormant 状態)
- Broker hosting が確定しないと:
  - main の Provider verify 結果が永続化されず、 PR の `can-i-deploy` が常に `unknown` で機能しない
  - 13 サービスへの Pact 拡大時に他チーム(50+ engineers の中)が Broker URL に接続できない
  - Glue Schema Registry(ADR-0019 で文法レイヤを担う)との two-layer 防御が片肺になる

選択肢:

1. **EKS self-host**(本プロジェクトの既存インフラに乗せる)
2. **Pactflow SaaS**(Pact 開発元の managed service)
3. **EC2 単独 VM + Docker Compose**(Broker container のみを VM 上で運用)
4. **Pactflow Free tier** で当面しのぐ

スケール想定:

- 13 サービス完全展開時の契約数: ~30〜50 経路(各業態 → Inventory Core, Master Data 配信 → 全サービス, audit/notification fan-out 等)
- Verify 頻度: PR 数 × 4 Provider matrix(現状 1〜2 PR/day → 将来 10〜20 PR/day)
- 同時 verify 接続数: ~10 並列(GitHub Actions matrix)

## Decision

**EKS self-host**を採用する。 既存 Aurora-C(common-base)クラスタ内に専用データベースを切り、 EKS の `pact-broker` namespace に Pact Broker を Deployment として展開する。

### 実装プロファイル

| 項目 | 選択 | 根拠 |
|---|---|---|
| 配置 | EKS prod クラスタ(ADR-0013)の `pact-broker` namespace | サービスメッシュ無し / namespace 隔離の既定パターン |
| バックエンド DB | Aurora-C 内の専用 DB `pact_broker`(ADR-0005 の common-base) | 既存 Aurora 運用に乗せる、 backup/PITR を共用 |
| Replica | 2 〜 3(`HorizontalPodAutoscaler` で CPU 60% 上限) | 単点障害排除、 verify burst に追従 |
| 認証 | Basic Auth(初期)→ OIDC(Cognito 連携、 Phase 2)| 段階導入 |
| ALB Ingress | **internal-only**(VPC 内 + GitHub Actions 用 OIDC IAM Role 経由) | Public 露出を避ける |
| TLS | ACM 証明書(`*.internal.example.com`)| 既存 ALB Controller パターン |
| Backup | Aurora 共通の continuous backup(7 日 PITR)+ 日次 snapshot(30 日保持)| ADR-0005 の DB ownership ルールに準拠 |
| Monitoring | Datadog APM(既存統合)+ `/diagnostic/status/heartbeat` ヘルスチェック | SRE alert 統合 |
| Image | `pactfoundation/pact-broker`(local と同一バージョン pin) | upstream 追従ルール |
| GitOps | ArgoCD で `pact-broker` Application を管理(ADR-0013 の方針に準拠) | 他サービスと同じ管理パターン |
| 所有 | Platform Team(`inventory-commons` と同じ Conway 境界) | 横断インフラの自然な所属 |

### CI / 開発者からの接続

- **CI(GitHub Actions)**: `PACT_BROKER_URL=https://pact-broker.internal.example.com` を repository secret に設定。 `PACT_BROKER_USERNAME/PASSWORD` も同様。 接続は GitHub-hosted runner から ALB の AWS-side endpoint(IAM 認証付き)を通す。 `pact-broker.yml` workflow が自動的に動き始める(現状 dormant が稼働化)。
- **開発者 local**: Broker への直接接続は **不要**。 開発者は引き続き `infra/pact-broker/docker-compose.pact-broker.yml` で local Broker を立てて検証する(ADR-0019 Phase 3)。 中央 Broker は CI 専用のソース・オブ・トゥルース。
- **Service-to-service**: 各サービスから Broker への runtime 接続は **無し**(契約は CI 時のみ参照)。 Broker は EKS 内の他 namespace と通信しない。

### 段階移行

| Phase | 内容 | trigger |
|---|---|---|
| 0(現状)| 全 dormant、 ローカル round-trip のみ | 本 ADR commit 時点 |
| 1 | Aurora-C に DB 切り出し + EKS Deployment + ALB + Basic Auth で立ち上げ | 本 ADR Accepted 後 1 sprint 以内 |
| 2 | Cognito OIDC 連携で Basic Auth を SSO に置換 | identity-broker MVP 完了後 |
| 3 | Pact Broker UI を社内エンジニア全員に公開(read-only)| Phase 1 安定運用 1 ヶ月後 |

## Consequences

### Positive

- **Data residency が日本(AWS Tokyo region)で完結**: J-SOX / 個人情報保護法 / 取引先契約上の compliance がシンプル(本プロジェクトの想定顧客 = 国内大手企業)。 SaaS 経路でデータが海外 region に出る議論を回避。
- **既存インフラへの完全な乗り合わせ**: ADR-0005(Aurora-C)/ ADR-0013(EKS)/ ADR-0007(Cognito)/ Datadog 統合。 新規ベンダ評価・契約・SOC2 レビューが不要。
- **コスト中立**: Aurora-C は既に provisioned で、 `pact_broker` DB 1 個追加は ~10MB 規模(契約 50 件 × メタデータ程度)で増分ほぼゼロ。 EKS pod は 2 replicas × 256MB / 250m CPU 程度で十分。
- **Pact 周辺ツールが完全動作**: `pact:publish` / `can-i-deploy` / consumer version selectors / verify result publish back が本番で機能(ADR-0019 Phase 3〜5 の労力が回収される)。
- **拡張性**: 13 サービス完全展開後に契約 50 件超になっても EKS pod / Aurora で問題なくスケールする(契約 1 件あたり KB オーダ)。

### Negative

- **運用負担が Platform Team に増える**: Pact Broker 自体の upgrade(2〜3 ヶ月ごと)/ TLS 証明書ローテ(ACM 自動だが切替 window)/ Cognito OIDC 設定。 SaaS なら全部 vendor が引き受ける。
- **Pact Broker 障害時に CI が止まる**: HA 構成(2 replicas + Aurora-C の Multi-AZ)で SPOF は回避するが、 Aurora-C 全体障害時は Pact Broker も止まる。 ADR-0005 で受容済みのリスクと同列。
- **upgrade lag のリスク**: SaaS なら自動追随する Pact 仕様変更(V5 / V6 など)を、 self-host では明示的に upgrade する必要がある。 平均 6 ヶ月〜 1 年の lag を許容(契約形式は上位互換が保証されている)。
- **GitHub Actions runner からの connectivity**: ALB を internal-only にすると、 GitHub-hosted runner からのアクセスに OIDC IAM Role 経由の DNS resolve が必要。 設定の一手間あり。

### Neutral

- **service mesh 導入時の設計影響なし**: Broker は他サービスと runtime 通信しないので、 Istio/App Mesh 導入決定(ADR-0013 で見送り中)に影響を受けない。
- **Aurora-C の他 DB(Identity / Notification / Workflow)との共存**: ADR-0005 のルール「サービス間 DB 直アクセス禁止」を Pact Broker にも適用。 Pact Broker サービスは Aurora-C 内の `pact_broker` DB のみアクセス。
- **`infra/pact-broker/docker-compose.pact-broker.yml` は廃止しない**: 開発者 local 検証用の用途で残す(Phase 3 で位置づけたとおり)。

## Alternatives considered

### Option 1: Pactflow SaaS(Team plan、 月額 ~$500)

Pact 開発元(SmartBear 傘下)が運営する managed Pact Broker。 Free / Starter / Team / Pro plan があり、 Team plan で 50 ユーザ・契約数無制限・SSO・SAML 等が利用可能。

**Rejected**:

- **Data residency**: Pactflow は AWS US-East 主体(EU region 拡張中だが Tokyo は未対応、 2026-04 時点)。 取引先・契約データは契約間の API 設計を表現するため、 法的な PII ではないが、 取引先 ID(`PARTNER-ACME` 等)・SKU 命名規則・在庫 location 名がスキーマに含まれる。 顧客契約上「すべての本番関連データを国内 region に閉じる」と謳っているプロジェクトでは説明コストが高い。
- **コスト**: 50+ engineers の規模で Team plan が必要。 月額 $500 × 12 = 年間 ~$6k。 EKS self-host の追加負荷 1 ヶ月あたり 0.1 FTE 程度(平均化)で trade-off。 大きく勝つほどではない。
- **既存インフラの活用度**: SaaS だと Aurora / Datadog / Cognito 統合が **使えない**。 別個に SAML 設定・別個の billing を増やす意味で、 既存 ops basis に乗らない。
- **vendor lock-in**: Pact 自体は OSS で contract 形式は標準。 Broker は実装の選択肢が広いので vendor 固定の懸念は小さいが、 SSO / RBAC / API token 等の周辺機能は SaaS と self-host で互換性があり、 必要に応じて self-host へ自前移行可能(ただし実施コストはそれなりにかかる)。

**Reconsider条件**:

- Platform Team が 0.1 FTE すら割けない事態(他の優先度に押される)
- Aurora-C 全体障害 / 容量逼迫で Pact Broker DB 隔離が必要になった
- 海外チームが追加され data residency 要件が逆に「グローバル accessible」に変わった

### Option 2: EC2 単独 VM + Docker Compose

EC2 1 台に Docker Compose で `pact-broker` + `postgres` を立てる。 `infra/pact-broker/` の compose ファイルをそのまま使う。

**Rejected**:

- **HA なし**: SPOF。 EC2 ホスト障害で Pact Broker が完全停止し、 全 PR の CI が止まる。
- **backup が個別運用**: Aurora の continuous backup が使えず、 EBS snapshot の cron が必要。 既存の DB backup 戦略(ADR-0005)から外れる。
- **EKS / GitOps エコシステムから外れる**: ArgoCD 管理外、 Datadog APM 直結でない、 IRSA も使えない。 横並び運用ができない 1 個の例外となる。
- **コスト的にも勝てない**: t3.medium 1 台 + Multi-AZ にするなら 2 台 = 月 ~$60。 EKS self-host は既存 cluster の余剰に乗るので増分ほぼゼロ。

### Option 3: Pactflow Free tier(契約 5 件・1 namespace 制限)

無料で SaaS Broker を使う。

**Rejected**:

- 既に **5 経路存在(ADR-0019 Phase 2.2 完了)** で free tier 上限に到達。 即座に Starter / Team plan が必要。
- public visibility が前提のプランがあり、 取引先 ID 等が含まれる contract を public 公開できない。

### Option 4: 別 SaaS(独立 Pact Broker hosting)

Pact 互換 Broker を提供する third-party SaaS(現状実質的に存在しない)。

**Rejected**: Pactflow が事実上唯一の選択肢。 別 SaaS 検討は無意味。

### Option 5: 「決めない」(現状維持で `pact-broker.yml` を dormant のまま運用)

CI workflow は dormant でも動かない、 Provider verify は Folder source のみ、 `can-i-deploy` は機能しない。

**Rejected**: ADR-0019 Phase 3〜5 の労力が宙に浮く。 「いずれ決める」は意思決定の先延ばしで、 Trunk-Based(ADR-0012)で短い PR を回している間にも contract 互換性の機械判定が無いままだと事故リスクが残る。

## Operational notes

### Pact Broker upgrade ポリシ

- **追従頻度**: pactfoundation/pact-broker の minor release を 3 ヶ月以内に local 検証 → prod 適用。 major release は 6 ヶ月以内に評価。
- **手順**: `infra/pact-broker/docker-compose.pact-broker.yml` の image tag を更新 → local round-trip 確認 → ArgoCD でプロモート → prod tag rollout。
- **互換性**: contract 形式(V3 / V4)の Broker 側互換性は保証されている。 upgrade で既存契約が壊れない前提。

### 契約データの分類と扱い

Pact contract に含まれる情報:

- **API 設計**(field 名 / 型): 公開 OpenAPI / GraphQL schema と同程度の機微度
- **Example 値**(`PARTNER-ACME` / `SKU-A` / `LOC-1` 等): 実 tenant データではなく代表値
- **テスト用識別子**(aggregateId 数値、 trace_id 等): 機微情報なし

→ **顧客向け契約上は「内部設計情報」相当**。 Broker は internal-only で十分(public 公開はしない)。

### 運用 SLA

- Pact Broker は CI のみ依存で **runtime 経路ではない**(在庫サービスの応答に Broker は関与しない)。 障害時の影響:
  - PR の `can-i-deploy` が `unknown` に戻る → merge 判定が手動チェックにフォールバック
  - 新規 contract の publish ができない → 既存契約での verify は引き続き機能
- 目標: **Best Effort SLA**(99% 月間稼働)。 在庫サービス本体の 99.9% より緩い。

## References

- ADR-0005: Database ownership(Aurora-C で同居する根拠)
- ADR-0007: Cognito + Identity Broker(将来 OIDC 連携の基盤)
- ADR-0013: EKS topology(`pact-broker` namespace の根拠)
- ADR-0019: Pact contract testing(本 ADR が担う後段)
- 関連ファイル:
  - [`infra/pact-broker/docker-compose.pact-broker.yml`](../../infra/pact-broker/docker-compose.pact-broker.yml) — local dev 用、本 ADR 後も継続
  - [`infra/pact-broker/README.md`](../../infra/pact-broker/README.md) — local 起動手順
  - [`.github/workflows/pact-broker.yml`](../../.github/workflows/pact-broker.yml) — ADR-0021 Phase 1 完了で稼働開始
- 外部参照:
  - [Pact Broker official Docker image](https://hub.docker.com/r/pactfoundation/pact-broker)
  - [Pactflow pricing](https://pactflow.io/pricing/) — Option 1 比較根拠
  - [Pact Broker administration](https://docs.pact.io/pact_broker/administration)
