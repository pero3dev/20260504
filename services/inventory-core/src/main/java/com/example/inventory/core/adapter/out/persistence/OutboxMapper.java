package com.example.inventory.core.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.inventory.commons.event.OutboxRecord;

/**
 * {@code outbox} テーブル用の MyBatis マッパー(Transactional Outbox、ADR-0009)。
 *
 * <p>役割は二つ:
 *
 * <ul>
 *   <li>append({@link #insert}) — 集約保存と同一トランザクションで書き込み。
 *   <li>drain({@link #pickUnpublished}, {@link #markPublished}) — OutboxPublisher が
 *       行ロック付きで取り出し、Kafka 発行成功後にマークする。
 * </ul>
 */
@Mapper
public interface OutboxMapper {

    int insert(@Param("rec") OutboxRecord record);

    /**
     * 未発行の行を行ロック付き({@code FOR UPDATE SKIP LOCKED})で最大 batchSize 件返す。 呼び出しは {@link
     * com.example.inventory.commons.event.OutboxPublisher#drainTenant} と同一トランザクション内で行うこと。
     */
    List<OutboxRecord> pickUnpublished(@Param("batchSize") int batchSize);

    int markPublished(@Param("eventId") long eventId);
}
