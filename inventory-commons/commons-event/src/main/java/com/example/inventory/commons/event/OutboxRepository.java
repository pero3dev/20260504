package com.example.inventory.commons.event;

import java.util.List;

/**
 * Outbox テーブルの操作ポート。
 *
 * <p>各サービスは MyBatis ベースの実装を提供する(Inventory Core の場合は {@code OutboxRepositoryImpl})。実装上の必須要件:
 *
 * <ul>
 *   <li>{@link #append(OutboxRecord)} は呼び出し元の DB トランザクションに参加すること (集約保存と同一トランザクションで永続化されるため)。
 *   <li>{@link #pickUnpublished(int)} は {@code SELECT ... FOR UPDATE SKIP LOCKED} を使って、複数 Pod
 *       が並行スキャンしても同一行を重複処理しないこと。
 *   <li>呼び出しは {@code TenantContext} 配下で実行される(テナントスキーマに対する操作)。
 * </ul>
 */
public interface OutboxRepository {

    /** 1件のイベントを outbox テーブルに追記する(現在の DB トランザクションに参加)。 */
    void append(OutboxRecord record);

    /** 未発行の行を最大 batchSize 件、行ロック付きで取得する。 */
    List<OutboxRecord> pickUnpublished(int batchSize);

    /** 指定 eventId の published フラグを true に更新する。 */
    void markPublished(long eventId);
}
