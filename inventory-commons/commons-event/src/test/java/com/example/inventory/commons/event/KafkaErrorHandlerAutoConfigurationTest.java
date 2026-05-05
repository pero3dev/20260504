package com.example.inventory.commons.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaErrorHandlerAutoConfigurationTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private final KafkaOperations<String, String> kafkaTemplate = mock(KafkaOperations.class);

    private final KafkaErrorHandlerAutoConfiguration autoConfig =
            new KafkaErrorHandlerAutoConfiguration();

    @Test
    void DLQ_recoverer_は元_topic_に_dlq_接尾辞を付けて発行先を解決する() {
        KafkaDlqProperties props = defaultProps();
        // dlqRecoverer 自体は組み立てられること(Bean 生成成功)を確認
        DeadLetterPublishingRecoverer recoverer = autoConfig.dlqRecoverer(kafkaTemplate, props);
        assertThat(recoverer).isNotNull();

        // 宛先解決ロジック本体は static method として分離してテスト
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("audit.log.v1", 2, 100L, "key", "value");
        TopicPartition target =
                KafkaErrorHandlerAutoConfiguration.resolveDlqDestination(record, props);

        assertThat(target.topic()).isEqualTo("audit.log.v1.dlq");
        assertThat(target.partition()).isEqualTo(-1);
    }

    @Test
    void カスタム_suffix_を尊重する() {
        KafkaDlqProperties props =
                new KafkaDlqProperties(true, "-deadletter", 3, 100L, 2.0, 2_000L);

        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("inventory.movement.v1", 0, 0L, null, null);
        TopicPartition target =
                KafkaErrorHandlerAutoConfiguration.resolveDlqDestination(record, props);

        assertThat(target.topic()).isEqualTo("inventory.movement.v1-deadletter");
    }

    @Test
    void DefaultErrorHandler_Bean_が生成される() {
        DeadLetterPublishingRecoverer recoverer =
                autoConfig.dlqRecoverer(kafkaTemplate, defaultProps());

        DefaultErrorHandler handler = autoConfig.kafkaErrorHandler(recoverer, defaultProps());

        assertThat(handler).isNotNull();
    }

    @Test
    void プロパティ既定値が補完される() {
        KafkaDlqProperties defaults = new KafkaDlqProperties(true, null, 0, 0, 0, 0);
        assertThat(defaults.suffix()).isEqualTo(".dlq");
        assertThat(defaults.maxRetries()).isEqualTo(3);
        assertThat(defaults.initialIntervalMs()).isEqualTo(100L);
        assertThat(defaults.multiplier()).isEqualTo(2.0);
        assertThat(defaults.maxIntervalMs()).isEqualTo(2_000L);
    }

    private static KafkaDlqProperties defaultProps() {
        return new KafkaDlqProperties(true, ".dlq", 3, 100L, 2.0, 2_000L);
    }
}
