package com.bank.transaction.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Listens to the debit-payment-requests topic. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitPaymentRequestListener {

    private final DebitPaymentRequestHandler handler;

    @KafkaListener(topics = "debit-payment-requests")
    public void onMessage(String message) {
        handler.handle(message)
                .subscribe(() -> { }, e -> log.error("Could not process {}: {}", message, e.toString()));
    }
}
