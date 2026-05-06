# ADR-0020: CI は Maven 内部並列(`-T 1C`)を採用、matrix build は条件付き移行

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: Architecture, Platform Team

## Context

サービス数が 13(共通基盤 9 + 業態 4)+ commons 10 モジュールで合計 26 reactor モジュールに到達した時点で、`mvn -B -ntp verify` の所要時間が **wall clock 約 2:00**(GitHub-hosted ubuntu-latest)に伸びた。Trunk-Based Development(ADR-0012)では PR 1 件あたりの CI フィードバック時間が開発体験に直結し、現状でも徐々に 「PR 投げて CI 待ち」のサイクルが冗長化してきた。

サービス追加が 13 で打ち止めではなく、Phase 2 で Workflow handler の各種 step Bean、 Integration Hub の adapter 群(EDI/AS2/SFTP/外部 EC)、 Analytics の追加集計 view などが入る予定で、放置すると 5 分・10 分と膨らむ可能性が高い。

選択肢として:

1. **Maven 内部並列**(`-T 1C` または `-T <N>`)— 1 つの runner で複数モジュールを並列ビルド
2. **GitHub Actions matrix build** — 各サービス別 runner で並列実行
3. **直列のまま**(現状)
4. **代替ビルドツール**(Bazel / Gradle 等)に移行

GitHub-hosted **ubuntu-latest standard runner は 4 vCPU**(2026 年現在の linux x64)。Maven 3.9 系の `-T 1C` は「1 thread per Core」の意味で、4 vCPU runner なら 4 並列。Reactor の依存グラフを見て独立に走らせられるモジュールは並列、依存があるものは順次。

## Decision

**`mvn -B -ntp -T 1C --fail-at-end verify` を CI workflow の標準コマンドとする。** GitHub Actions matrix build は **条件付き移行** とし、現状規模では採用しない。

### コマンド変更の根拠

- **ubuntu-latest 4 vCPU の活用**: Reactor の依存グラフ並列実行で wall clock を約 50% 短縮(ローカル測定: 2:16 → 1:10、実装コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1))。
- **cache 戦略は既存のまま**: `actions/setup-java@v4` の `cache: maven` がそのまま効く。matrix にすると runner ごとに cache 参照が必要で複雑化。
- **failure isolation は既存の artifact upload で十分**: 失敗時に `surefire-reports` / `failsafe-reports` を upload する既存 step が機能。
- **`--fail-at-end` で並列下でも全モジュールの結果が見える**: 1 モジュール失敗で他が止まらないので、複数失敗の同時把握が可能。

### matrix build への移行条件

以下の **いずれか** を満たした時点で再評価し、matrix build に移行する:

| 条件 | 閾値 | 根拠 |
|---|---|---|
| 単一 runner の wall clock | 10 分超 | 開発者の PR 待ち体感の臨界点(業界一般指標) |
| サービス数(reactor の business module 数) | 25 超 | 4 vCPU 並列でも捌ききれない数 |
| 1 モジュールあたりの平均 verify 時間 | 1 分超 | 単一 runner では `-T 1C` でも余白が無くなる |
| Provider Pact verify(ADR-0019 Phase 2)が複雑化 | 各サービスで個別 setup が必要 | matrix で setup を分離した方が読みやすい |

### matrix 移行時の構造(参考)

将来 matrix を採用するときの想定形:

```
jobs:
  build-base:
    # commons-* と全モジュールの compile + Spotless check + ArchUnit
    # ~/.m2 を artifact として upload
  
  test-services:
    needs: build-base
    strategy:
      matrix:
        service: [inventory-core, inventory-read-model, ..., integration-hub]
    steps:
      - download base artifact (m2 cache)
      - mvn -pl services/${{ matrix.service }} verify
```

注意: matrix にすると ~/.m2 cache pass が必要(各 runner は独立)。Pact ファイルなどの artifact pass も必要に応じて。

## Consequences

### Positive

- **PR フィードバックが約半分に短縮**(ローカル実測 ~50%)。Trunk-Based Development(ADR-0012)の小さい PR 連続フローと相性が良い。
- **設定変更が 1 行のみ**(`mvn verify` → `mvn -T 1C verify`)。コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1) で実装済。
- **CI コスト中立**: 同じ runner 1 台で並列するだけ。GitHub Actions の課金は分単位なので、wall clock 短縮 = コスト削減。matrix にすると同時 runner 数が増えてコストも線形に増える。
- **既存の failure handling が機能**: `--fail-at-end` で並列実行下でも 全モジュール結果がレポートされ、 surefire artifact upload で詳細追跡可能。
- **将来の matrix 移行が「条件 + 移行先構造」まで明文化済み**: 必要になったときの判断と実装が再現可能。

### Negative

- **並列実行時のログが時系列に混在**: 失敗ジャンプが少しわかりにくい。`mvn -B`(batch mode)で輸出フォーマットを揃えてあるので致命的ではないが、 matrix 個別 runner に比べると読みにくい。
- **vCPU 数の前提依存**: ubuntu-latest が将来 8 vCPU 以上に増えれば自動恩恵あり、逆に 2 vCPU に減ると効果半減。GitHub の標準 runner 仕様変更には影響を受ける(2026 時点で 4 vCPU、 SLA 上の保証ではないが慣習的に維持されている)。
- **memory 競合のリスク**: 並列で複数 Spring Context を立ち上げる statepful test(`@SpringBootTest`)が同時実行されると runner の RAM(7-16GB)を圧迫する可能性。ローカル測定では問題なかったが、サービス追加で再評価が必要。

### Neutral

- **Maven daemon (mvnd)** の検討は範囲外。daemon はローカル開発体感には効くが CI で都度プロセス起動と相殺。Maven 自体への移行(ADR-0010 で確定)を変えない。
- **e2e-tests モジュールは引き続き skip**(ADR-0014)。`-T 1C` の影響なし。
- **Spotless check は別 job のまま**。並列 verify と独立に走る。

## Alternatives considered

### Option 1: GitHub Actions matrix build を最初から採用

各サービスを別 runner で並列実行。

**Rejected (now, may revisit)**:

- 現状規模(26 モジュール、 wall clock 1〜2 分)では cost-benefit が見合わない。`-T 1C` だけで 50% 短縮できるなら、matrix の追加複雑性に対するメリットが小さい。
- matrix では各 runner で `commons-*` を都度ビルド or artifact pass する必要がある。Reactor 構造をそのまま活用できる `-T 1C` の方が設定が簡素。
- GitHub Actions の同時 runner 数(無料 plan は 20、Team は 60)を消費する。 matrix 採用時は plan 内の他 workflow との競合も発生。
- 上記の **移行条件**(10 分超等)に達したら採用する未来オプションとして残す。本 ADR は「条件付き Rejected」。

### Option 2: 直列のまま(現状からの変更なし)

`mvn verify` のまま。

**Rejected**: 現状すでに 2 分超で、サービス追加に対するスケーラビリティが無い。50% 短縮の機会を逃す理由はない(変更コストはほぼゼロ)。

### Option 3: Bazel に移行

Bazel は incremental build と並列実行が桁違いに高速。

**Rejected**: 

- ADR-0010 で Maven 採用を決定。Java エコシステム親和性、 Spring Boot starter / Maven plugin 群、 OpenAPI generator plugin 等が Maven 前提。
- Bazel への移行は build script の全面書き換え + チームの学習コスト。CI 短縮目的だけでは正当化できない。
- 26 モジュールでのコールドビルド時間 1-2 分は Bazel 移行でなくても許容範囲。

### Option 4: Gradle に移行

Gradle は parallel build がデフォルトで動き、 build cache が強力。

**Rejected**: Option 3 と同様の理由(ADR-0010)。Gradle のスクリプトは Groovy/Kotlin DSL で、 reactor / dependencyManagement / parent pom の構造を Maven のように pure XML で表現する仕様も維持できなくなる(Java 25 ホスト、 各 plugin の枯れ具合の議論)。

### Option 5: `-T 4`(固定スレッド数)

`-T 1C` ではなく `-T 4` で 4 スレッド固定。

**Rejected**: vCPU 数が将来変わったときに自動追従しない。`-T 1C` はマシンに合わせて動的に決まり、ローカル開発(マシンによって 4 / 8 / 16 vCPU 等異なる)でも一貫した設定で動く。

## Operational notes

### マシン要件再評価のサイン

以下が観測されたら Memory 競合 or vCPU 不足を疑い、 matrix への移行を検討:

- `mvn verify` の wall clock が **連続 3 ビルドで 5 分超**
- `OutOfMemoryError` の散発的発生
- `surefire` の `forkCount` を絞らないと安定しない症状

### Future complement との関係

- **ADR-0014**(cross-service E2E は CI 外し): `e2e-tests` モジュールを skip する点は本 ADR で変えない。
- **ADR-0019**(Pact Phase 2 で Provider verify): 並列実行下でも Pact Verify は単一サービス内のテストに閉じるので、 `-T 1C` の影響を受けない。Phase 3 で Pact Broker と連携するときは別途 `can-i-deploy` step を追加(matrix 採用検討の trigger になる可能性あり)。

## References

- ADR-0010: Maven 採用(Gradle/Bazel への移行を Reject する根拠)
- ADR-0012: Trunk-Based Development with Feature Flags(高速 CI フィードバックが必要な根拠)
- ADR-0014: Cross-service E2E deferred to local-only(`e2e-tests` skip の前提)
- ADR-0019: Pact contract testing(Phase 3 で Broker 導入時の matrix 移行 trigger)
- 実装コミット: [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1)
- 関連ファイル:
  - [.github/workflows/ci.yml](../.github/workflows/ci.yml) — 本 ADR の対象 workflow
  - [pom.xml](../pom.xml) — reactor 親 pom
- 外部参照:
  - [Maven parallel builds](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
  - [GitHub-hosted runner specs](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners) — 4 vCPU の根拠
