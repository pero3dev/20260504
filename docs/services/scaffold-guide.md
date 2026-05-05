# 新規サービス スキャフォールド ガイド

13サービス予定の在庫管理 SaaS で、新しいサービスを追加するときに **「迷わず動く骨組みを1日で揃える」** ためのレシピ集。

リファレンス実装(4 パターン):
- `services/inventory-core/` — DBあり / 書込権威 / Outbox / **Bridge 方式マルチテナンシ**
- `services/inventory-read-model/` — DBなし / 読取専用 / Kafka 消費 + Redis 投影
- `services/identity-broker/` — DBあり / **Pool 方式マルチテナンシ** / **JWT 発行サービス**
- `services/audit-service/` — DBあり / Pool / **Kafka 消費専用**(REST API なし、JWT 検証も無し)

CLAUDE.md(規約集)と併せて読むこと。本書は **「規約をどう守るか」**(How)、CLAUDE.md は **「何が規約か」**(What)。

---

## 0. 決定マトリクス

新サービスの性質を最初に決める。配線が変わる。

| 質問 | YES の場合の影響 | NO の場合の影響 |
|---|---|---|
| **業務データの DB を持つか?** | Aurora 接続、MyBatis、Flyway、Outbox(at-least-once) | Stateless、`DirectKafkaDomainEventPublisher`(at-most-once) |
| **書込 API があるか?** | `@Auditable`、`@Transactional`、楽観ロック、ドメインイベント | 読取のみなら不要 |
| **マルチテナントデータを扱うか?** | TenantContextFilter + MyBatis Interceptor が必須(Bridge) / tenant_id 列 + RLS(Pool) | テナント中立なら filter のみ |
| **テナント中立のエンドポイントを持つか?** (例: ログイン、JWKS、ヘルス) | TenantContext 不在を前提にした分岐が要 — 後述 §16 参照 | — |
| **JWT を発行する側か?**(認証ハブ等) | `commons-security` の `PlatformSecurity.applyDefaults` を使わない例外パターン — §17 参照 | 通常パターンで OK |
| **Kafka を購読するか?** | `@KafkaListener` + 手動ack + 冪等性チェック | producer のみなら listener 不要 |
| **REST API を公開するか?** | `docs/openapi/<service>.yaml` + 生成 + コントローラ | 内部ジョブのみなら不要 |
| **Redis を使うか?** | `spring-boot-starter-data-redis` | DB or in-memory only |

13サービス別の典型(参考):

| サービス | DB | 書込API | 読取API | Kafka 消費 | Kafka 発行 | Redis |
|---|---|---|---|---|---|---|
| Inventory Core | ◎ | ◎ | × (Read Modelに委譲) | × | ◎(イベント) | × |
| Inventory Read Model | × | × | ◎ | ◎ | (auditのみ) | ◎ |
| Master Data | ◎ | ◎ | ◎ | × | ◎(変更通知) | △(キャッシュ) |
| Audit | × | × | × (Athena経由) | ◎(全イベント) | × | × |
| Notification | △(購読管理用) | × | × | ◎ | × | △(WS接続管理) |
| Workflow | ◎ | ◎ | ◎ | ◎(承認連携) | ◎ | × |
| Integration Hub adapters | △ | ◎ | ◎ | ◎ | ◎ | × |
| Analytics | ◎(集計DB) | × | ◎ | ◎(全イベント) | × | × |
| 業態系(Retail/Manufacturing/3PL/Wholesale) | ◎ | ◎ | ◎ | ◎ | ◎ | × |

---

## 1. Maven モジュール作成

### 1-1. 親 POM の modules に追加

`pom.xml`:
```xml
<modules>
    ...
    <module>services/<service-name></module>
</modules>
```

### 1-2. サービス POM(`services/<service-name>/pom.xml`)

ベース構造は `services/inventory-core/pom.xml` または `services/inventory-read-model/pom.xml` をコピー。違いの判断は決定マトリクス参照。

**最低限の依存(全サービス共通):**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example.inventory</groupId>
            <artifactId>commons-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-tenant</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-error</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-security</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-resilience</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-observability</artifactId>
    </dependency>
    <!-- 認証あり API -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <!-- テスト -->
    <dependency>
        <groupId>com.example.inventory</groupId>
        <artifactId>commons-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**書込ありサービスは追加:**
```xml
<dependency>
    <groupId>com.example.inventory</groupId>
    <artifactId>commons-event</artifactId>
</dependency>
<dependency>
    <groupId>com.example.inventory</groupId>
    <artifactId>commons-persistence</artifactId>
</dependency>
<dependency>
    <groupId>com.example.inventory</groupId>
    <artifactId>commons-audit</artifactId>
</dependency>
```

**DB あり追加:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Kafka producer/consumer:**
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

**Redis:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Spring Boot Maven プラグイン:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**REST API 公開する場合は OpenAPI generator も:**
リファレンス: `services/inventory-core/pom.xml` の `openapi-generator-maven-plugin` 節を丸ごとコピーし、`apiPackage` / `modelPackage` をサービスに合わせて変更。

---

## 2. パッケージ構成

ベースパッケージ: `com.example.inventory.<servicename>`(例: `com.example.inventory.notification`)

```
src/main/java/com/example/inventory/<servicename>/
├── <Servicename>Application.java         @SpringBootApplication
├── domain/
│   ├── model/                            集約 / Entity / Value Object
│   └── event/                            ドメインイベント(DomainEvent 実装)
├── application/
│   ├── port/
│   │   ├── in/                           ユースケース interface + コマンド
│   │   └── out/                          リポジトリ等の出力ポート
│   └── usecase/                          @Service 実装クラス
├── adapter/
│   ├── in/
│   │   ├── rest/                         @RestController
│   │   ├── graphql/                      GraphQL リゾルバ(BFF のみ)
│   │   └── kafka/                        @KafkaListener
│   └── out/
│       ├── persistence/                  MyBatis @Mapper 実装
│       ├── kafka/                        Producer ラッパ
│       ├── redis/                        Redis アクセス
│       └── external/                     外部 API クライアント
└── config/                               Spring @Configuration
```

ArchUnit が層方向を強制する(`HexagonalLayerRules`)。違反は CI 失敗。

---

## 3. アプリケーションクラス

```java
package com.example.inventory.<servicename>;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Kafka 購読する場合のみ:
// import org.springframework.kafka.annotation.EnableKafka;
// Outbox publisher 走らせる場合(=書込ありサービス)のみ:
// import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// @EnableKafka
// @EnableScheduling
public class <Servicename>Application {

    public static void main(String[] args) {
        SpringApplication.run(<Servicename>Application.class, args);
    }
}
```

---

## 4. Security 設定

`config/SecurityConfig.java`:

```java
@Configuration
public class SecurityConfig {

    private static final String[] PERMIT_ALL = {
            "/actuator/health/**",
            "/actuator/info",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   PlatformSecurity platform) throws Exception {
        return platform.applyDefaults(http)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(PERMIT_ALL).permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
```

これだけ。共通部分(CSRF/CORS/JWT/RFC 7807/TenantContextFilter)は `commons-security` の `PlatformSecurity` が吸収する。

特殊な permitAll パターンや認可ルールがあれば、`PERMIT_ALL` を編集 or `authorizeHttpRequests` をカスタマイズ。

---

## 5. application.yml

最低限:

```yaml
spring:
  application:
    name: <service-name>
  config:
    import: classpath:application-resilience-defaults.yml
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${IDENTITY_BROKER_ISSUER:https://idp.example.com/}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true

application:
  env: ${APP_ENV:local}

logging:
  config: classpath:logback-spring.xml
```

DB あり追加:

```yaml
spring:
  datasource:
    url: ${<SVC>_DB_URL:jdbc:postgresql://localhost:5432/<service-name>}
    username: ${<SVC>_DB_USER:<service-name>_app}
    password: ${<SVC>_DB_PASSWORD:dev}
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
  flyway:
    enabled: false   # K8s Job で先行実行(ADR-0013)

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

Kafka 追加:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:                  # producer 使うサービスのみ
      acks: all
      properties:
        enable.idempotence: true
    consumer:                  # consumer 使うサービスのみ
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      group-id: <service-name>
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: manual_immediate
      concurrency: 3
```

Outbox publisher を走らせるサービス(=書込あり):

```yaml
platform:
  snowflake:
    worker-id: ${POD_ORDINAL:0}
  outbox:
    enabled: true
    publisher-enabled: true     # テスト時のみ false で停止可
    poll-interval: PT1S
    batch-size: 200
    tenants:
      - <tenant-id>             # 本番は Identity 連携実装に置換
```

Redis 追加:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 1s
```

---

## 6. ドメイン層

集約は **純粋 Java POJO**(record でも class でも可)。フレームワーク注釈(@Entity, @Table 等)は付けない。

例: 集約ルート

```java
public final class Foo {
    private final FooId id;
    // ... 他のフィールド ...
    private long version;                    // 楽観ロック用
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Foo create(...) { ... }
    public static Foo restore(...) { ... }   // 永続化からの再構築

    public void doSomething(...) {
        // 業務ルール検査(失敗時 BusinessException)
        // 状態変更
        // pendingEvents.add(new SomethingHappenedEvent(...));
    }

    public List<DomainEvent> pendingEvents() { ... }
    public void clearPendingEvents() { ... }
}
```

**ID は `commons-persistence` の `SnowflakeIdGenerator` から採番。** auto-config で全サービスに Bean が用意されている。

**ドメインイベントは `commons-event.DomainEvent` を実装。** post-state(変更後の値)を含めて、Read Model 等が単一イベントで完全再構築できるようにする。

---

## 7. アプリケーション層

ユースケース interface(入力ポート):

```java
public interface DoSomethingUseCase {
    SomethingResult doSomething(DoSomethingCommand command);
}
```

実装クラス(`@Service` で配線):

```java
@Service
public class DoSomethingService implements DoSomethingUseCase {

    private final FooRepository repository;

    public DoSomethingService(FooRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional   // DBあり書込のみ
    @Auditable(action = "FOO_DO_SOMETHING",
               targetType = "Foo",
               targetIdExpression = "#command.fooId")
    public SomethingResult doSomething(DoSomethingCommand command) {
        Foo foo = repository.findById(...).orElseThrow(...);
        foo.doSomething(...);
        repository.save(foo);                 // ここで domain events が outbox に書かれる
        return new SomethingResult(...);
    }
}
```

**`@Auditable` はDB変更操作には必須。** ArchUnit でいずれ強制(現状はコードレビューで担保)。

---

## 8. アダプタ層

### 入力 REST(OpenAPI スキーマファースト)

1. `docs/openapi/<service>.yaml` を書く。`getXxx`/`createXxx` 等を operationId で定義。
2. POM の `openapi-generator-maven-plugin` で生成(`mvn generate-sources`)。
3. コントローラは生成 interface を `implements`:

```java
@RestController
public class FooController implements FooApi {
    private final DoSomethingUseCase useCase;
    public FooController(DoSomethingUseCase useCase) { this.useCase = useCase; }

    @Override
    public ResponseEntity<FooResponse> getFoo(Long fooId) { ... }
}
```

スキーマと実装の乖離は **コンパイルエラー**。

### 入力 Kafka

```java
@Component
public class FooEventListener {
    private final ApplyFooService service;
    private final ObjectMapper objectMapper;
    // ...
    @KafkaListener(topics = "foo.event.v1", groupId = "${spring.application.name}")
    public void on(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        String tenantId = headerValue(record, "tenant_id");
        try {
            TenantContext.set(new TenantId(tenantId));
            FooMessage msg = objectMapper.readValue(record.value(), FooMessage.class);
            service.apply(msg);                // 冪等性チェックは service 側
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
    }
}
```

メッセージ DTO は **意図的に発行元の event クラスから import せず別定義**(マイクロサービス境界の維持)。Glue Schema Registry 統合後は自動生成版に置換。

### 出力 永続化(MyBatis)

`adapter/out/persistence/`:
- `FooRow.java`(record、フラット)
- `FooMapper.java`(`@Mapper` interface)
- `FooMapper.xml`(SQL 定義、`src/main/resources/mapper/` に配置)
- `FooRepositoryImpl.java`(`@Repository`、`AggregateRepository` 実装)

楽観ロック規約:
- 更新 SQL は `WHERE version = #{expectedVersion}` 必須
- `OptimisticLockSupport.verify(rowsAffected, ...)` で 0行影響時は `OptimisticLockException`

ドメインイベント発行:
- `repository.save(aggregate)` 内で `aggregate.pendingEvents()` を `DomainEventPublisher.publish` に渡す
- `DefaultDomainEventPublisher`(commons-event auto-config)が outbox に書く

`OutboxRepositoryImpl` の追加:
- `OutboxMapper` の `pickUnpublished` / `markPublished` / `insert` を MyBatis で実装
- これがあれば `OutboxPublisher` が自動的に Kafka へドレイン

### 出力 Kafka(直接)

書込のないサービスでも、監査・通知などで Kafka に発行する場合は `commons-event` の `DirectKafkaDomainEventPublisher` が auto-config される。何もしなくて良い(KafkaTemplate Bean が存在すれば)。

---

## 9. DB マイグレーション(DBあり時のみ)

`src/main/resources/db/migration/V1__<service>_baseline.sql`:

```sql
-- 本マイグレーションは現在の search_path に対して実行される。
-- ADR-0003 (Bridge方式マルチテナンシ)に基づき、ランナーが事前に
-- search_path をテナントスキーマへ切替える。

CREATE TABLE foo (
    id              BIGINT       NOT NULL PRIMARY KEY,    -- Snowflake
    -- ... ビジネスフィールド ...
    version         BIGINT       NOT NULL DEFAULT 0
);

-- 書込権威を持つサービスは outbox テーブルも必要(commons-event Outbox 用)
CREATE TABLE outbox (
    event_id        BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    schema_version  VARCHAR(16)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    payload         JSONB        NOT NULL,
    trace_id        VARCHAR(64),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published       BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published = FALSE;
```

本番では K8s Job が tenant ごとに実行する。`spring.flyway.enabled=false` で起動時の自動実行は無効化(設計事故防止)。

---

## 10. テスト

### 必須(`src/test/java/.../<service>/`):

#### 10-1. ArchUnit

`architecture/ArchitectureTest.java`:

```java
@AnalyzeClasses(packages = "com.example.inventory.<servicename>",
                importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule layered = HexagonalLayerRules.layered();
    @ArchTest static final ArchRule appNotAdapter = HexagonalLayerRules.applicationDoesNotDependOnAdapter();
    @ArchTest static final ArchRule reposInAdapter = HexagonalLayerRules.repositoryImplsAreInAdapter();
}
```

#### 10-2. ドメイン単体テスト

集約のビジネスロジックをユニットテスト。Spring 不要。`InventoryTest.java` 参照。

#### 10-3. ユースケース単体テスト

Mockito で出力ポートをモック、ユースケースの結果を assert。

### 推奨(時間に応じて):

#### 10-4. 統合テスト(`*IntegrationTest.java`)

Testcontainers + `@SpringBootTest` で API レベル検証。`ReservationE2EIntegrationTest` 参照。

JWT バイパスは `@TestConfiguration` で `JwtDecoder` Bean を上書き。

---

## 11. CI 統合

新サービスは親 POM の `<modules>` に追加するだけで `mvn -B -ntp verify` で自動検証される(ci.yml の build-and-test ジョブで網羅)。

CodeQL も自動的に新サービスを解析する(`codeql.yml`)。

---

## 12. よくある落とし穴

### 12-1. `@Auditable` 付け忘れ
DB を変更するユースケースに `@Auditable` を付けないと、J-SOX 統制対象外になり監査漏れ。コードレビューで確認。将来 ArchUnit で強制。

### 12-2. ドメインイベントから `version` を抜く
Read Model が冪等性チェックに使うため、書込発行イベントには **post-state(変更後の version)** を必ず含める。

### 12-3. テナントスキーマ未対応
DB アクセス時に `TenantContext.required()` が必須。`MyBatisConfig` で `TenantSearchPathInterceptor` を SqlSessionFactory に登録するのを忘れない。inventory-core を参照。

### 12-4. Outbox 書込忘れ
`Repository.save(aggregate)` で `pendingEvents()` を取り出して publish する処理を実装しない → イベントが失われる。`InventoryRepositoryImpl` を参照。

### 12-5. Kafka メッセージ DTO を発行元から import
マイクロサービス境界の漏れ。受信側はトピックスキーマだけを契約とし、独自に DTO を定義する(`InventoryMovementMessage` 参照)。

### 12-6. Spring Security 自前で組む
共通設定は `commons-security` の `PlatformSecurity` を使う。直接 HttpSecurity を弄ると、CSRF/CORS/RFC 7807 エラー処理 等が抜ける。

### 12-7. `spring.flyway.enabled=true` のまま本番
Flyway の本番実行は K8s Job(ADR-0013)。アプリ起動時実行は禁止。

### 12-8. Kafka producer の冪等性無効
`enable.idempotence: true` + `acks: all` を必ず設定。ペアでない設定は性能と整合性のどちらも崩す。

### 12-9. JWT に tenant_id が無いユーザがいる
`TenantContextFilter` は tenant_id クレーム無しの JWT を黙って通過させる。テナント不要なエンドポイントは permit-all に追加、必須なエンドポイントは別途 `TenantContext.required()` で fail-fast。

### 12-10. Pool 方式サービスに Bridge 用 interceptor を入れる
**Pool 方式(共通基盤系: Identity / Notification / Workflow / Integration Hub config 等)では `TenantSearchPathInterceptor` も `MyBatisConfig` も不要。**
`MyBatisConfig.java` を作って interceptor を登録するのは Bridge 方式のサービスのみ。Pool は `tenant_id` 列 + RLS で論理分離する。詳細は §15。

### 12-11. JWT 発行サービスで `PlatformSecurity.applyDefaults` を使う
**Identity Broker のように JWT を発行する側は OAuth2 リソースサーバ設定を適用しない。** 自分が発行したトークンを自分で検証する必要が無く、JWT 検証フィルタを噛ませると認証ループが起きる。詳細は §17。

### 12-12-bis. ユースケースコマンドの機微情報を `@AuditMask` し忘れる
`@Auditable` メソッドの第1引数(コマンド/リクエスト型)は **JSON シリアライズして監査ペイロードの `inputJson` に格納される**。パスワード・カード番号・JWT・APIキー等が引数に含まれる場合、record コンポーネント/フィールドに `@AuditMask` を付けないと audit ストアに生で残る(J-SOX 監査でも実運用でも致命的)。

```java
public record AuthenticateCommand(
        String email,
        @AuditMask String password   // ← inputJson では "***"
) { }
```

**ArchUnit で CI 強制済み**: `commons-test.AuditMaskingRules.sensitiveFieldsInCommandsAreMasked()` が `*Command` 型の機微名フィールド(`password / passwd / pwd / secret / token / apikey / api_key / credential / privatekey / private_key / ssn / creditcard / credit_card`、大文字小文字無視)に `@AuditMask` を必須化。各サービスの `ArchitectureTest` で組み込み済み。

機微名でない命名(例: `apiSecretValue` を `magicValue` にする等)で迂回するコードはレビューで指摘すること。命名規約として `*Pwd / *Token / *Secret / *ApiKey` 等を機微名と認識する点に留意。

### 12-13. Bridge 方式サービスのテナントレス監査
`AuditEventEmitter` の SYSTEM フォールバック(§16)は **Pool 方式サービス専用**。Bridge 方式のサービスでテナントレス処理(管理API等)に `@Auditable` を付けると、SYSTEM フォールバックで `tenant_platform` スキーマへ search_path が切り替わり、スキーマ不在で SQL 失敗する。
解決策: 当該リクエストの実テナントを解決して `TenantContext.set(...)` した上で `@Auditable` を発火させるか、Pool 方式の管理用サブサービスに切り出す。

---

## 13. ファイル構成チェックリスト

新サービス追加時のチェックリスト(完成度順):

- [ ] `services/<name>/pom.xml`(親 POM の modules にも追加)
- [ ] `<Name>Application.java`
- [ ] `domain/model/<MainAggregate>.java`
- [ ] `application/port/in/<UseCase>.java` + コマンド
- [ ] `application/port/out/<Repository>.java`
- [ ] `application/usecase/<Service>.java`
- [ ] `adapter/in/rest/<Controller>.java`(REST 公開時)
- [ ] `adapter/in/kafka/<Listener>.java` + メッセージ DTO(購読時)
- [ ] `adapter/out/persistence/<Mapper>.java` + XML + `<RepositoryImpl>.java`(DB あり時)
- [ ] `adapter/out/persistence/OutboxRepositoryImpl.java`(書込ありサービス必須)
- [ ] `config/SecurityConfig.java`(JWT 発行サービスは §17 の例外パターン)
- [ ] `config/MyBatisConfig.java`(**Bridge 方式のみ**、Pool 方式は不要 — §15 参照)
- [ ] `resources/application.yml`
- [ ] `resources/mapper/*.xml`(MyBatis 使用時)
- [ ] `resources/db/migration/V1__*.sql`(DB あり時)
- [ ] `docs/openapi/<service>.yaml`(REST 公開時)
- [ ] `test/architecture/ArchitectureTest.java`
- [ ] ドメイン/ユースケースの単体テスト

---

## 14. 参照

- アーキテクチャ規約: [CLAUDE.md](../../CLAUDE.md)
- アーキテクチャ決定: [docs/adr/](../adr/)
- リファレンス実装(DBあり / Bridge / 書込権威 + Outbox): [services/inventory-core](../../services/inventory-core/)
- リファレンス実装(DBなし / 投影 + Direct Kafka): [services/inventory-read-model](../../services/inventory-read-model/)
- リファレンス実装(DBあり / Pool / JWT 発行): [services/identity-broker](../../services/identity-broker/)
- リファレンス実装(DBあり / Pool / Kafka 消費専用 + ハッシュチェーン): [services/audit-service](../../services/audit-service/)
- 連結 E2E: [e2e-tests](../../e2e-tests/)

---

## 15. マルチテナンシ方式別ガイド (Bridge / Pool)

ADR-0003 で **Bridge** と **Pool** の使い分けが決まっている。実装時の差分:

### Bridge 方式(業務データ系: Inventory Core / Master Data / 業態系 / Workflow 等)

- スキーマ毎にテナント分離(`tenant_<id>` スキーマ)
- 接続取得時に `SET search_path TO tenant_<id>, public` を発行
- 必要な配線:
  - `commons-tenant` 依存
  - `MyBatisConfig.java` で `TenantSearchPathInterceptor` を SqlSessionFactory に登録
  - SQL 内でスキーマ名を明示しない(`search_path` で切替)
  - Flyway マイグレーションは "tenant ごとに 1 回" 実行(K8s Job)

```java
@Configuration
public class MyBatisConfig {
    @Bean public TenantSearchPathInterceptor tenantSearchPathInterceptor() {
        return new TenantSearchPathInterceptor();
    }
    @Bean public SqlSessionFactoryBeanCustomizer registerTenantInterceptor(
            TenantSearchPathInterceptor interceptor) {
        return f -> f.setPlugins(interceptor);
    }
}
```

### Pool 方式(共通基盤系: Identity / Notification / Workflow 設定 / Integration Hub 設定 / Audit 設定 等)

- 単一スキーマ、各テーブルに `tenant_id` 列
- Row-Level Security ポリシーで論理分離(必要に応じて)
- 必要な配線:
  - `commons-tenant` 依存(`TenantContext` 自体は使う)
  - **`MyBatisConfig.java` 不要**(interceptor 登録不要)
  - SQL は普通の WHERE 句で `tenant_id` を絞る、または PG 接続セッション変数 + RLS
  - Flyway マイグレーションは "1 回だけ" 実行(共通スキーマ)
- ロケーション: `services/identity-broker/` を参照

**判断指針:** データ量がテナント毎に大きい(在庫トランザクション等)→ Bridge。データ量が小さく管理データ中心(ユーザー、ロール、設定等)→ Pool。

---

## 16. テナントレスエンドポイントの扱い

ログイン、JWKS 公開、内部ヘルス、Webhook 受信(認証前)等は **TenantContext が空の状態** で動く。注意点:

### permitAll 必須
`SecurityConfig` の `requestMatchers(...).permitAll()` に追加。`commons-tenant.TenantContextFilter` は JWT 不在/tenant_id 不在の場合は何もしないので素通りする。

### `@Auditable` を使ってよい(SYSTEM テナントフォールバック対応済み)
`AuditEventEmitter` は emit 直前に TenantContext が空であれば自動で {@link TenantId#SYSTEM}({@code "platform"}) をセットし、emit 後に元の状態に戻す。これにより **テナントレスエンドポイントでも `@Auditable` がそのまま使える**。

- Outbox 行は `tenant_id="platform"` で記録される
- `AuditEvent.operatorTenantId` は AuditableAspect 起動時の TenantContext 値(空なら `"unknown"`)を保持

**注意:** SYSTEM フォールバックは Pool 方式サービス向け設計。Bridge 方式のサービスは `tenant_platform` スキーマを持たないため、SYSTEM フォールバックで MyBatis インターセプタが失敗する。Bridge 方式サービスのテナントレス監査が必要になったら、当該テナントを明示的に解決して TenantContext.set(...) する。

### 業務エンドポイントは fail-fast
逆に、テナント必須なエンドポイントで JWT に `tenant_id` クレームが欠けると、コードのどこかで `TenantContext.required()` が `IllegalStateException` を投げる。クライアントには 500 で見える。リクエスト先頭で明示的にチェックして 400/401 を返すと親切(共通フィルタへ昇格候補)。

---

## 17. JWT 発行サービスの SecurityConfig 例外

13 サービスのうち **JWT を発行する側のサービス**(現在は Identity Broker のみ)は、通常パターンと違う Security 設定を使う。

### 通常サービス(JWT 検証側)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                               PlatformSecurity platform) throws Exception {
    return platform.applyDefaults(http)        // ← OAuth2 リソースサーバ + RFC 7807 を適用
            .authorizeHttpRequests(reg -> reg
                    .requestMatchers(PERMIT_ALL).permitAll()
                    .anyRequest().authenticated())
            .build();
}
```

### JWT 発行サービス(Identity Broker)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())  // ログイン公開
            .build();
}
```

**理由:**
- Identity Broker は **JWT を発行する側**。自分が発行したトークンの検証は不要。
- `PlatformSecurity.applyDefaults` は OAuth2 リソースサーバ + JWT 検証を入れる。これを Identity Broker に適用するとループになる。
- 将来、管理 API(ユーザー登録・ロール変更等、認証必須)を追加するときは、別の `SecurityFilterChain` Bean を `@Order` で優先度を分けて並列定義する。

### 下流サービスの設定切替

JWT 検証側のサービスは `application.yml` で発行者の JWKS を指す:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${IDENTITY_JWKS_URI:https://idp.example.com/.well-known/jwks.json}
          # issuer-uri は使わない(OIDC discovery が不要)
```

### 内部 Kafka 消費専用サービス(Audit Service 等)

JWT を発行も検証もしない、内部の Kafka コンシューマ専用サービス(REST 業務 API なし、actuator のみ)。

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())  // actuator のみ
            .build();
}
```

**理由:**
- 公開する HTTP エンドポイントは actuator のみ。本番では NetworkPolicy / Service Mesh で内部閉域に
- JWT 検証を入れると Kafka 内部呼び出しに無駄な仕掛けが入る
- 万一外部公開する管理 API を後付けする場合は `@Order` で別 SecurityFilterChain を並列定義(Identity Broker と同じ手法)

該当サービス: Audit Service / 将来の Notification Service など。

---

## 18. JWT 署名鍵管理

Identity Broker の MVP は **起動時にプロセス内で RSA 鍵ペアを生成**(`JwtKeyConfig.java`)している。これは開発・テスト専用。

### 本番運用で必須の対応
1. **永続的な鍵をストレージから読み込む**:
   - **AWS Secrets Manager** に PEM 形式で保存(Identity Broker のみアクセス可能な IAM ロール)
   - **AWS KMS** の RSA キーペア(より安全、署名処理を KMS 側で行う方式も検討)
2. **鍵ローテーション**:
   - 新旧 2 つの公開鍵を JWKS に同時に出す期間を設ける(古い JWT が失効するまで)
   - 古い鍵は `keyID` 別で残し、新しい署名は新 keyID で行う
3. **JWKS キャッシュの考慮**:
   - 下流サービスは JWKS をローカルキャッシュする(Spring の `NimbusJwtDecoder` は自動)
   - ローテーション時の不整合期間を最小化するため、TTL を短く(例: 5 分)

### Bean の差し替え方
```java
@Bean
@Profile("production")  // 本番のみ
public JWKSource<SecurityContext> jwkSource() {
    return new SecretsManagerBackedJwkSource(...);   // 自前実装
}
```

MVP の `JwtKeyConfig.jwkSource()` は `@Profile("!production")` を付けて開発専用にする(本ガイド執筆時点では未対応、TODO)。

---

## 19. 並行性パターン

### 19-1. Postgres advisory lock(同キー直列化)

Audit Service のように、Kafka パーティション分散で **同じテナント(または同じ集約)に対する更新が複数 consumer で並行発生** するシナリオ。SELECT FOR UPDATE は INSERT との競合を防げないため、advisory lock を使う。

```sql
SELECT pg_advisory_xact_lock(hashtext(:tenantId))
```

- transaction-scoped:トランザクション終了時に自動解放
- `hashtext()` で文字列キーを 32bit int に縮約(advisory lock のキー型に合わせる)
- ロック範囲が「キーごと」なので、別キーは並列のまま

MyBatis での書き方は `services/audit-service/.../mapper/AuditRecordMapper.xml` を参照。

**いつ使うか:**
- 同キーへの「読み込み → 計算 → 書き込み」の race を排除したい(audit-service のチェーン append 等)
- パーティション内でも序列が保証されない場合(複数パーティションをまたぐとき)

**いつ使わないか:**
- 単一行の更新だけ(Postgres の MVCC + UNIQUE 制約 + 楽観ロックで十分)
- スループットが極端に高い場合(advisory lock は 1 トランザクション 1 取得に限定)

### 19-2. UNIQUE 制約 + 冪等エラーハンドリング

at-least-once 配信(Kafka)を effectively-once に変える鉄板パターン。

```java
try {
    repository.append(record);   // INSERT に event_id UNIQUE 制約
    return APPENDED;
} catch (DuplicateKeyException e) {
    // 既に処理済み event_id。冪等にスキップ。
    return DUPLICATE_SKIPPED;
}
```

事前に `existsByEventId` を呼んでも、advisory lock の隙間で同イベントが入る可能性がある(レース条件)。**両方の防御が必要**:
1. 事前 `existsByEventId` チェック → 通常はここで弾く
2. INSERT 時の DuplicateKeyException catch → レース時の最終ガード

### 19-3. Kafka コンシューマの手動 ack

`ack-mode: manual_immediate` + リスナで明示 `ack.acknowledge()`。DB commit が成功した後だけ ack するパターン:

```java
@KafkaListener(...)
public void onMessage(ConsumerRecord<String, String> rec, Acknowledgment ack) {
    useCase.process(...);   // @Transactional commit
    ack.acknowledge();       // commit 後に offset commit
}
```

サービス障害時(crash 等)、未 ack のメッセージは別 consumer or 同 consumer 再起動時に再配信される。冪等性(19-2)とセットで使う。

### 19-4. DLQ(Dead Letter Queue)ハンドラ

`commons-event` の `KafkaErrorHandlerAutoConfiguration` が、Kafka 消費を行うサービス全てに DLQ を自動配線する。**サービス側で何も書かなくて良い**(無効化したい場合は `platform.kafka.dlq.enabled=false`)。

挙動:
- 消費失敗時、Exponential Backoff で 3 回までリトライ(初期 100ms、倍率 2.0、上限 2 秒)
- リトライ尽きると `<original-topic>.dlq` へ failed record を発行
- `DeserializationException` / `IllegalArgumentException` 等は即時 DLQ(リトライしない)

設定例(`application.yml`):
```yaml
platform:
  kafka:
    dlq:
      enabled: true
      suffix: .dlq          # 既定 .dlq
      max-retries: 3
      initial-interval-ms: 100
      multiplier: 2.0
      max-interval-ms: 2000
```

**本番運用上の必須対応:**
- DLQ トピックを Terraform で事前作成(本番では auto-create を無効化推奨)
- Datadog で `<topic>.dlq` の流入数アラート(0 でない時刻に通知)
- DLQ から本トピックへの再投入は **人的判断 + 専用ツール**(自動再投入禁止 — 同じバグループに陥る)

### 19-5. リトライ + DLQ + 冪等の関係

3 つの仕組みを正しく組み合わせる:
| 失敗の性質 | 対応 |
|---|---|
| 一時的(DB 接続瞬断、Kafka リバランス中等) | **リトライで吸収**(19-4) |
| 永続的(ペイロード不正、DB 制約違反等) | **即時 DLQ**(19-4 の non-retryable 一覧) |
| 重複配信(リトライ中の再配信、再起動後の重複) | **冪等で吸収**(19-2) |

「リトライしすぎて DLQ に行かない」は監視で見つけにくいバグ。`max-retries` は控えめに(本ガイドの既定 3 回が目安)。

---

## 20. 複数サービスが同じ Postgres を共有する場合

開発・テスト環境では 1 つの Postgres インスタンスに全サービスを乗せる(本番は ADR-0005 の通り 3 クラスタに分散)。複数サービスのスキーマ衝突回避が必要。

### 20-1. Flyway マイグレーションパスを per-service に分ける

```
services/<service>/src/main/resources/db/migration/<service>/V1__*.sql
```

`application.yml`:
```yaml
spring:
  flyway:
    locations: classpath:db/migration/<service>
```

これにより、複数サービスの migration jar が同一 classpath に乗っても V1 衝突しない(各サービスの V1 は独立した locations)。

### 20-2. Flyway 履歴テーブルをスキーマ別に分離

複数サービスが同じスキーマを使うと `flyway_schema_history` も衝突する。回避するには **サービスごとにスキーマを分けて Flyway を別実行**:

```java
// audit-service migration
Flyway.configure()
    .schemas("audit_service")            // ← サービス別スキーマ
    .locations("classpath:db/migration/audit-service")
    .load().migrate();

// identity-broker migration
Flyway.configure()
    .schemas("public")
    .locations("classpath:db/migration/identity-broker")
    .load().migrate();
```

各スキーマに `<schema>.flyway_schema_history` が独立して作られるため、両者の V1 が共存可能。

### 20-3. Pool 方式サービスは `currentSchema=` URL パラメータで search_path 固定

Pool 方式(MyBatis interceptor 不使用)で、複数サービスが同じ Postgres を共有する場合、各サービスのテーブルが衝突しないようスキーマを分ける。Spring Datasource の URL に:

```
jdbc:postgresql://...?currentSchema=<schema_name>
```

これで search_path がデフォルトで `<schema_name>, public` になり、MyBatis SQL が無修飾でそのスキーマのテーブルにアクセスする。

例: audit-service は `?currentSchema=audit_service`、identity-broker は `?currentSchema=public`(または省略)。

### 20-4. 本番では別クラスタなのでこの問題は無くなる

ADR-0005 に従い、本番は 3 つの Aurora クラスタに分散(ホットパス / 業態系 / 共通系)。同一クラスタ内でも各サービスは独自の **論理データベース** を持つため、スキーマ衝突は発生しない。本セクションは主に **開発・テスト環境(単一 Postgres)** を念頭に置く。
