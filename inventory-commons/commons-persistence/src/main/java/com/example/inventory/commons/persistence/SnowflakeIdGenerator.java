package com.example.inventory.commons.persistence;

/**
 * インプロセスで動作する Snowflake 形式 64bit ID ジェネレータ(ADR-0011)。
 *
 * <p>レイアウト:
 *
 * <pre>
 *   sign(1) | timestamp_ms_since_epoch(41) | worker_id(10) | sequence(12)
 * </pre>
 *
 * <p>worker_id は起動時に {@code POD_ORDINAL} / {@code HOSTNAME} 由来の値で設定し、 [0, 1023]
 * の範囲を検証する。Pod間のNTP時刻ズレは worker_id 軸で同ms衝突を防ぐため、 1ミリ秒程度までは許容される。
 */
public final class SnowflakeIdGenerator {

    private static final long EPOCH_MILLIS = 1_704_067_200_000L; // 2024-01-01T00:00:00Z

    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId は [0, " + MAX_WORKER_ID + "] の範囲が必要です(指定値: " + workerId + ")");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            // クロックが巻き戻った → ID衝突リスクを避けるため拒否する。
            throw new IllegalStateException(
                    "システムクロックが巻き戻りました。" + (lastTimestamp - now) + "ms 分のID生成を拒否します。");
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH_MILLIS) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private static long waitNextMillis(long lastTimestamp) {
        long now;
        do {
            now = System.currentTimeMillis();
        } while (now <= lastTimestamp);
        return now;
    }
}
