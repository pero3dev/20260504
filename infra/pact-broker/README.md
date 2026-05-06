# Pact Broker(local dev / CI)

ADR-0019 Phase 3 で導入。 Consumer-driven contract test の publish 先と、 `can-i-deploy` 判定のための contract repository を兼ねる。

## 起動

```bash
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml up -d
```

UI: <http://localhost:9292>(Basic Auth: `pact_user` / `pact_password`)

## 停止

```bash
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml down
# volume も消す場合:
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml down -v
```

## 使い方

### Consumer 側 — 契約を publish

inventory-core で Consumer Pact test を実行 → `target/pacts/*.json` 生成 → Broker に publish。

```bash
# 1. consumer test を走らせて pact 生成
mvn -pl services/inventory-core -Dtest='*ConsumerPactTest' test

# 2. Broker に publish(Broker URL/credential は env / system property で渡す)
mvn -pl services/inventory-core pact:publish \
  -Dpact.broker.url=http://localhost:9292 \
  -Dpact.broker.username=pact_user \
  -Dpact.broker.password=pact_password \
  -Dpact.consumer.version=$(git rev-parse --short HEAD) \
  -Dpact.consumer.tags=$(git branch --show-current)
```

### Provider 側 — Broker から契約を取得して verify

`PACT_BROKER_URL` 環境変数が設定されていれば各 Provider Pact test は自動で Broker を読みに行く(後述の Phase 3 実装で追加)。 未設定なら従来どおり `target/pacts/` 配下の PactFolder から読む。

```bash
PACT_BROKER_URL=http://localhost:9292 \
PACT_BROKER_USERNAME=pact_user \
PACT_BROKER_PASSWORD=pact_password \
mvn -pl services/wholesale -Dtest='*ProviderPactTest' \
  -Dpact.providerVerifier.enabled=true test
```

### can-i-deploy

```bash
docker run --rm pactfoundation/pact-cli:latest \
  broker can-i-deploy \
  --broker-base-url http://host.docker.internal:9292 \
  --broker-username pact_user \
  --broker-password pact_password \
  --pacticipant inventory-core \
  --version $(git rev-parse --short HEAD) \
  --to-environment production
```

## 認証情報

Local dev のデフォルト credential は **`pact_user` / `pact_password`** で固定(`docker-compose.pact-broker.yml`)。 CI / 共有環境では Broker 自体を別ホスト(EKS の admin namespace か Pactflow SaaS)に立て、 secret は GitHub Actions の `PACT_BROKER_URL` / `PACT_BROKER_TOKEN` 経由で配る方針(運用詳細は **Phase 3 完了後に別 ADR** で確定)。

## 参照

- ADR-0019: Pact による Consumer-driven 契約テストの段階導入
- 上流ドキュメント: <https://docs.pact.io/pact_broker/docker_images/pactfoundation>
