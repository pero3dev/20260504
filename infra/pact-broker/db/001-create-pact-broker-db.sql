-- Pact Broker DB 初期化スクリプト(ADR-0021 Phase 1)。
-- Aurora-C(common-base、 ADR-0005)内に専用 DB と専用ユーザを切り出す。
--
-- 実行タイミング: Phase 1 立ち上げ時に **Aurora master account から 1 度だけ** 手動実行。
-- ADR-0009 Flyway 経路は使わない(これは Pact Broker サービス自身のスキーマではなく、
-- DB そのものの作成。 Pact Broker 自身の table は Pact Broker container 起動時に
-- 内部 migration が自動実行する)。
--
-- ⚠️ 実行前に AWS Secrets Manager に pact_broker user の password を登録しておくこと。
--     ExternalSecretsOperator 経由で K8s Secret に同期される。

-- 1. Pact Broker 専用ロール(login user)。
CREATE ROLE pact_broker WITH LOGIN PASSWORD 'REPLACE_VIA_AWS_SECRETS_MANAGER';

-- 2. Pact Broker 専用 DB。
CREATE DATABASE pact_broker
  OWNER pact_broker
  ENCODING 'UTF8'
  LC_COLLATE 'C'
  LC_CTYPE 'C'
  TEMPLATE template0;

-- 3. 公開 schema を pact_broker user に絞る(他 service が誤って読まないため)。
REVOKE ALL ON DATABASE pact_broker FROM PUBLIC;
GRANT CONNECT ON DATABASE pact_broker TO pact_broker;

-- 4. 接続切替後、 schema レベルの権限を pact_broker に集約。
\c pact_broker
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO pact_broker;

-- 確認:
--   psql -h <aurora-c-host> -U pact_broker -d pact_broker -c '\conninfo'
-- が成功し、 他 service の DB user では接続できないこと。
