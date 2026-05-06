# ADR-0018: 業務取消と補償取消で集約メソッドを分ける

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: Architecture, Platform Team

## Context

ADR-0017 の follow-up で「Wholesale / Retail/EC の取消 → reserved を解放」フローを実装した(commit `e1b2ca2`)。新フローは:

```
業務側で取消 API 呼出
   ↓
Order.cancel() → CANCELLED 遷移 + <業態>.order.cancelled.v1 発行
   ↓ (Kafka)
inventory-core: reserved → available に release(明細毎)
```

ただし、Wholesale / Retail/EC には別系統の取消経路もある: **Reserve 失敗の補償**(ADR-0016 の業態別補償トピック)。流れは:

```
業務側で受注 → wholesale.order.placed.v1 発行
   ↓
inventory-core: reserve(明細毎) → 1 行で InsufficientStock → @Transactional rollback
   ↓
inventory-core: wholesale.reservation.failed.v1 発行(REQUIRES_NEW)
   ↓ (Kafka)
Wholesale: HandleReservationFailureService → Order.cancel() ?
```

この `Order.cancel()` で `wholesale.order.cancelled.v1` を発行すると、Inventory Core 側の release listener が動く。しかし **Reserve は @Transactional で rollback されているため、reserved には何も乗っていない**。release リスナは `Inventory.release(qty)` を呼んで `InsufficientReservedException` で失敗する。これは設計バグ。

要件:

- 業務側起因の取消 → release イベント発行(reserved を戻す)
- Reserve 失敗の補償経由の取消 → release イベント発行**しない**(reserved は乗っていない)
- 同じ `cancel()` API ではこの 2 つを区別できない

呼出側に判断を委ねる(「補償経由かどうか」を呼出側が知る)、または集約メソッドを分ける必要がある。

## Decision

**集約に 2 つのメソッドを切る。** 業務取消と補償取消で意図を別の名前で表現し、呼出側の責務を「どちらの意図か」だけにする(イベント発行有無の判断はドメインに閉じる)。

```java
public final class Order {

    /** 業務側起因のキャンセル。release イベント発行あり。 */
    public void cancel() { ... emit OrderCancelledEvent ... }

    /** 補償経由のキャンセル(Reserve 失敗で reserved が乗っていない)。状態のみ更新、イベント無し。 */
    public void cancelAfterReservationFailure() { ... }
}
```

呼出側のマッピング:

| 呼出元 | メソッド | 理由 |
|---|---|---|
| REST `POST /v1/orders/{id}/cancel`(業務 API、未実装) | `cancel()` | 業務側で意思決定された取消 → reserved を戻す |
| `HandleReservationFailureService`(両業態の補償ハンドラ) | `cancelAfterReservationFailure()` | Reserve 失敗時は reserved が乗っていない → release を送ると InsufficientReserved |

両メソッドの共通仕様:

- `CANCELLED` への再呼出は no-op(冪等)、イベント発行なし
- `SHIPPED` 状態からは `IllegalStateException`(出荷済の取消は返品扱いで別フロー、ADR-0017)
- 状態遷移自体は両者で同じ: `PLACED → CANCELLED`

差は **イベント発行の有無のみ** だが、呼出側がブール真偽値を渡すよりも 2 つの動詞で表現するほうが意図が読み取りやすい(後述 Option 1 比較)。

## Consequences

### Positive

- **呼出側のミスが構造的に減る**。`cancel(boolean releaseInventory)` だと、コードレビュー時に「`true` / `false` どっちが正しい?」を毎回考える必要がある。`cancelAfterReservationFailure()` は名前そのものが文脈を伝える。
- **テストが対称的に書ける**。「業務取消は OrderCancelledEvent を 1 個発行」「補償取消はイベント発行なし」を別ケースで明示でき、リグレッションが目に見える。
- **将来「キャンセル理由」の追加が個別にできる**。例えば業務取消には reason を必須化、補償取消には errorCode を持たせる、と片方だけ拡張可能。
- **新業態(Subscription / Marketplace)追加時に判断が再現可能**。「Reserve が成功する経路 → cancel()」「補償経由 → cancelAfterReservationFailure()」と機械的に当てはめられる。

### Negative

- **集約 API が増える**。`Order` のキャンセル系メソッドが 2 つに割れるので、ドメインの認知負荷が少し上がる。ただし関連動詞が並ぶことで全体像はむしろ把握しやすい。
- **完全な対称性ではない**。`cancelAfterReservationFailure()` のような長い名前は、名前空間としてやや異質。それでも「短い名前 + ブール値で意図を消す」より良いと判断した。
- **Manufacturing には現状不要**(後述)。今後 Reserve+Ship に類似する取消フローを持つ業態が出ない限り、本パターンは Wholesale / Retail/EC だけにとどまる。

### Neutral

- **MVP ではキャンセル業務 API(REST)はまだ未実装**。本 ADR は将来 API が追加されたときの前提を凍結する役割もある。
- 現実的な使用状況: `cancelAfterReservationFailure()` は `HandleReservationFailureService` だけが呼ぶ。`cancel()` は将来の業務 API が呼ぶ。テストは両方を直接呼んで挙動を検証する。

## Alternatives considered

### Option 1: ブール引数で切り替え `cancel(boolean releaseInventory)`

```java
order.cancel(true);   // 業務取消(release あり)
order.cancel(false);  // 補償取消
```

**Rejected**: ブール真偽値でドメインの意図を表すのは典型的なアンチパターン。コードレビューで「`true` は何の意味だったか」を毎回確認する必要が出る。enum で渡す案(`cancel(CancelReason.BUSINESS)` 等)も検討したが、結局はメソッド名で表現した方が呼出側の可読性が高い。テストで mock を組むときも、引数のバリエーション毎にケースを書くより、メソッド毎にケースを書く方がフレームワーク的にも自然。

### Option 2: 同一の `cancel()` で、inventory-core 側の release listener が `InsufficientReserved` を catch して skip

```java
// inventory-core ReleaseForOrderService
try {
    inventory.release(qty);
} catch (InsufficientReservedException e) {
    LOG.warn("補償経由の cancel?スキップ");
    return; // 黙ってスキップ
}
```

**Rejected**: 真の不整合(reserved が想定外に減っている、データ破損)も silent fail させてしまう。ADR-0008 の監査要件と矛盾する(整合性違反は検出して人が見るべき)。「Reserve 失敗の補償だから release が無駄」という業務知識を inventory-core 側にリークさせるのも責務上望ましくない。

### Option 3: Saga state を `Order` に記録(`reservationConfirmed: boolean`)

`Order` が「reserved に乗ったかどうか」を内部状態として持ち、`cancel()` がその状態を見て release イベントを発行するか判断する。

**Rejected**: 集約境界を超えた状態管理になる。`Order` は自テナントの注文状態を持つだけで、Inventory Core の reservation 状態を知るべきではない(ADR-0002 / 0009 の集約原則違反)。`Order` 側で「自分の reservation 結果」を eventual に同期する仕組みも作れるが、複雑性に見合わない。

### Option 4: 補償ハンドラ専用に新しい集約状態を追加

`OrderStatus.CANCELLED_BY_FAILURE` のような新ステートを作る。

**Rejected**: 結局、業務取消と補償取消は **論理的には同じ最終状態**。状態を分けるのは UI / レポートで「キャンセル理由」を区別したいときには有用だが、それは別のメタフィールド(reason / errorCode)で表現すべき。

## Scope

### 適用される業態

- ✅ Wholesale `Order`(本 ADR で実装済)
- ✅ Retail/EC `Order`(本 ADR で実装済)
- ❌ Manufacturing `WorkOrder`: ADR-0017 で Reserve+Ship パターンを採用しており、`release()` のために reserved を残す期間が無い。現時点で `cancelAfterReservationFailure()` 相当は不要

### 将来の業態追加時の判断フロー

```
1. ADR-0017 で Reserve のみ vs Reserve+Ship を選ぶ
2. Reserve のみ(reserved を一定期間保持)を選んだ場合:
   - cancel() / cancelAfterReservationFailure() の 2 メソッドを切る
   - <業態>.order.cancelled.v1 トピックを定義
   - inventory-core 側に <業態>OrderCancelledListener を追加
3. Reserve+Ship(即時消費)を選んだ場合:
   - cancel() 1 つだけ。release は不要(reserved は元から無い)
```

## References

- ADR-0009: Transactional Outbox(イベント発行の信頼性根拠)
- ADR-0015: Saga choreography(本パターンが choreography で完結する根拠)
- ADR-0016: 業態別補償トピック(`<業態>.order.cancelled.v1` 命名規約)
- ADR-0017: Reserve のみ vs Reserve+Ship(本 ADR の適用条件)
- 実装コミット: `e1b2ca2` — 業務取消時の reserved 解放フロー
- E2E IT: `services/inventory-core/.../KafkaIntegrationE2ETest#wholesaleOrderPlacedThenCancelled_drivesReserveThenRelease`(commit `c6dd5c0`)
- 関連コード:
  - `services/wholesale/.../Order.java#cancel`, `cancelAfterReservationFailure`
  - `services/retail-ec/.../Order.java#cancel`, `cancelAfterReservationFailure`
  - `services/inventory-core/.../ReleaseForOrderService.java`
  - `services/wholesale/.../HandleReservationFailureService.java`(`cancelAfterReservationFailure` を呼ぶ)
  - `services/retail-ec/.../HandleReservationFailureService.java`(同)
