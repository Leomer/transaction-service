package com.bank.transaction.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Publishes the result of debit payments. Failures are logged, never thrown. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultPublisher {

    public static final String TOPIC = "debit-payment-results";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Format: COMPLETED|paymentId|balanceAfter. */
    public void completed(String paymentId, BigDecimal balanceAfter) {
        send(paymentId, "COMPLETED|" + paymentId + "|" + balanceAfter.toPlainString());
    }

    /** Format: FAILED|paymentId|reason (separators removed from the reason). */
    public void failed(String paymentId, String reason) {
        String clean = reason == null ? "Unknown error" : reason.replace("|", "/").replace("\n", " ");
        send(paymentId, "FAILED|" + paymentId + "|" + clean);
    }

    private void send(String key, String message) {
        try {
            kafkaTemplate.send(TOPIC, key, message).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Could not publish {}: {}", message, error.toString());
                } else {
                    log.info("Payment result published: {}", message);
                }
            });
        } catch (Exception e) {
            log.error("Could not publish {}: {}", message, e.toString());
        }
    }
}
