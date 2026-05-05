package com.example.inventory.notification.application.port.out;

/** メール送信が SES / SMTP 等の外部システムで失敗した。 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
