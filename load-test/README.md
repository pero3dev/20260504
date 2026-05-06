# Load Test (Phase A)

CLAUDE.md で凍結された性能目標(1M SKU、100 拠点、10K 同時ユーザ、**100M inventory transactions/day** ≈ peak ~11.6k TPS)に対し、 **Phase 2 着手前のベースライン取得** を行うスイート。Architecture 妥当性の go/no-go ゲートとして機能する。

## Phase 範囲

**Phase A(本ディレクトリ、本セッション完成)**

- Inventory Core 単独の **Reserve API throughput** ベースライン
- ローカル Docker Compose で Postgres + Kafka を起動、Inventory Core は host で `mvn spring-boot:run`
- k6 でシナリオ実装、ramp-up + sustained でスループット計測

**Phase B(別タスク、後日)**

- Outbox → Kafka publish latency
- Read Model 投影遅延
- 業態系統合シナリオ(`POST /v1/sales-orders` → reserve → ship のフルパス)
- Datadog APM 連携
- 結果に基づき ADR-0021 起草(SLO 設定 + ボトルネック対応)

## 前提

- Docker Desktop 4.71+ が起動済み(CLAUDE.md「Local vs CI test boundaries」参照)
- Windows なら TCP daemon 有効化:Settings → General → Expose daemon on `tcp://localhost:2375`
- k6 binary ≥ 0.50(Docker から走らせる場合は不要)
- JDK 21
- 5 分以上の連続実行に耐えるマシン(目安: 16GB RAM、4+ vCPU)

## ディレクトリ構成

```
load-test/
  README.md                        # 本ファイル
  docker-compose.load.yml          # Postgres + Kafka 起動(Inventory Core は host 側で起動)
  k6/
    reserve-throughput.js          # Reserve API throughput シナリオ
  scripts/
    seed.sql                       # テナント schema 作成 + Inventory/SKU シード
    get-token.sh                   # identity-broker から JWT 取得(Bash)
    get-token.ps1                  # 同上(PowerShell)
```

## 実行手順

### 1) インフラ起動(Postgres + Kafka)

```bash
cd load-test
docker compose -f docker-compose.load.yml up -d
```

確認:
```bash
docker compose -f docker-compose.load.yml ps
# postgres / kafka が "Up" になっていればOK
```

### 2) Inventory Core 起動 + シード投入

別 terminal で:

```bash
# まず DB を初期化
psql -h localhost -p 5433 -U test -d inventory_core -f load-test/scripts/seed.sql
# (パスワード: test)
```

#### 方法 B(推奨、クイック起動 - Phase A.1 で実装)

`loadtest` profile で起動すると JWT 認証バイパス + tenant は HTTP header(既定 `dev`)から取得する設定になり、 identity-broker 不要で Reserve API を叩ける。

```bash
cd ../  # repo root
INVENTORY_CORE_DB_URL=jdbc:postgresql://localhost:5433/inventory_core \
  INVENTORY_CORE_DB_USER=test \
  INVENTORY_CORE_DB_PASSWORD=test \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9095 \
  mvn -pl services/inventory-core spring-boot:run \
      -Dspring-boot.run.profiles=loadtest
```

起動ログで `LOADTEST PROFILE: 認証バイパス有効。本番禁止。` が出ることを確認。

> ⚠️ **本 profile を本番で有効化しないこと**。 K8s ConfigMap / EKS deployment で `loadtest` を誤設定しない仕組み(IaC レビュー / NetworkPolicy 閉域)を Phase 3 で追加する。

#### 方法 A(本格、 identity-broker 経由)

production-like な経路で実 JWT を流したい場合:

```bash
# Inventory Core(本番 profile)起動
INVENTORY_CORE_DB_URL=jdbc:postgresql://localhost:5433/inventory_core \
  INVENTORY_CORE_DB_USER=test \
  INVENTORY_CORE_DB_PASSWORD=test \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9095 \
  IDENTITY_BROKER_ISSUER=http://localhost:8081/ \
  mvn -pl services/inventory-core spring-boot:run

# 別 terminal で identity-broker を起動(別途設定が必要)
mvn -pl services/identity-broker spring-boot:run
```

### 3) JWT 取得

#### 方法 B の場合

JWT は不要。 k6 シナリオが `X-Tenant-Id: dev` header を付けるだけ:

```bash
TOKEN=""  # 空でもよい(loadtest profile は permitAll)
```

#### 方法 A の場合

identity-broker から 2 段階フロー(`/v1/auth/sessions` で sessionToken 取得 → `/v1/auth/tenant-sessions` で accessToken 取得):

```bash
# Bash
TOKEN=$(./scripts/get-token.sh)
echo $TOKEN

# PowerShell
$env:TOKEN = .\scripts\get-token.ps1
```

環境変数で上書き可能: `LOAD_TEST_EMAIL` / `LOAD_TEST_PASSWORD` / `LOAD_TEST_TENANT` / `IDENTITY_BROKER_ISSUER`

### 4) k6 でシナリオ実行

```bash
# Docker から実行(推奨、k6 をインストールしなくて済む)
docker run --rm -i --network host \
  -e TOKEN=$TOKEN \
  -e BASE_URL=http://localhost:8080 \
  -v $PWD/k6:/scripts \
  grafana/k6 run /scripts/reserve-throughput.js

# または k6 binary が手元にあるなら
TOKEN=$TOKEN BASE_URL=http://localhost:8080 \
  k6 run k6/reserve-throughput.js
```

### 5) 結果の見方

k6 が標準出力に summary を吐く。重要な数値:

```
http_req_duration..........: avg=Xms  min=Xms  med=Xms  max=Xms  p(90)=Xms  p(95)=Xms
http_reqs..................: <count> <RPS>
http_req_failed............: X.XX%
checks.....................: X.XX%
```

**判定基準(初回の go/no-go ゲート)**:

| 指標 | OK | NG → Phase 2 で要対策 |
|---|---|---|
| sustained RPS / Pod | ≥ 1000 | < 500 |
| p95 latency | < 200ms | > 500ms |
| error rate | < 0.1% | > 1% |

11.6k TPS peak を達成するには、 Pod / instance 必要数:
- 1000 RPS/Pod なら 12 Pod
- 500 RPS/Pod なら 24 Pod

## 注意事項

- **Bridge 方式マルチテナンシ** で `tenant_dev` schema が必要(`scripts/seed.sql` で作成)
- **Outbox publisher** が動いている前提で Reserve は成功する。 Kafka publish が詰まると Reserve のレイテンシも遅くなる(ボトルネック検出のシナリオとしては有効)
- k6 のメモリは VU 数 × ペイロードサイズで増える。100 VU 程度なら問題なし、 1000 VU 以上は別マシンから走らせる方が無難

## トラブルシュート

| 症状 | 対応 |
|---|---|
| `Connection refused` to Postgres | `docker compose ps` で Postgres up 確認、 ポート 5433(load-test 用、本番 5432 と分離)に注意 |
| `Connection refused` to Kafka | `docker compose ps` で Kafka up 確認、 ポート 9095(load-test 用)に注意 |
| Inventory Core が `OAuthIssuerSelfDiscoveryException` で起動失敗 | identity-broker を起動するか、Phase A.1 の `loadtest` profile を待つ |
| JWT が `401` で reject される | token 期限切れ、 `get-token.sh` で再発行 |
| k6 から `dial tcp ::1:8080: connect: connection refused` | `--network host` 漏れ、 または Inventory Core が起動していない |
