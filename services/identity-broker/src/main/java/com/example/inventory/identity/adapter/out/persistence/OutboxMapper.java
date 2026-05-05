package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.inventory.commons.event.OutboxRecord;

/**
 * Identity Broker 用の outbox テーブルアクセサ(Transactional Outbox、ADR-0009)。 MVP の auth ユースケースでは直接の domain
 * event 発行は無いが、将来の管理操作 (ユーザー登録、ロール変更 等)で使うため初期から用意する。
 */
@Mapper
public interface OutboxMapper {

    int insert(@Param("rec") OutboxRecord record);

    List<OutboxRecord> pickUnpublished(@Param("batchSize") int batchSize);

    int markPublished(@Param("eventId") long eventId);
}
