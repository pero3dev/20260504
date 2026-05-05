package com.example.inventory.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification — 業務イベント駆動の通知配信サービス。
 *
 * <p>Pool 方式マルチテナンシ(ADR-0003)。`tenant_id` 列で論理分離する共通 DB を使う。 Kafka topic
 * から業務イベントを購読し、テンプレートで本文を組み立てて送信する。 送信器はポート抽象化されており、MVP は stdout、本番は SES / SendGrid 等に差し替え。
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
