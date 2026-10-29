package com.bank.transaction.event;

import com.bank.transaction.dto.OperationRequest;
import com.bank.transaction.service.TransactionService;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Processes debit payment requests as withdrawals from the card's primary account. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitPaymentRequestHandler {

    private final TransactionService transactionService;
    private final PaymentResultPublisher publisher;

    /** Handles REQUESTED|paymentId|cardNumber|accountNumber|amount. */
    public Completable handle(String message) {
        String[] parts = message.split("\\|", -1);
        if (parts.length != 5 || !"REQUESTED".equals(parts[0])) {
            log.warn("Malformed payment request ignored: {}", message);
            return Completable.complete();
        }
        String paymentId = parts[1];
        BigDecimal amount;
        try {
            amount = new BigDecimal(parts[4]);
        } catch (NumberFormatException e) {
            publisher.failed(paymentId, "Invalid amount");
            return Completable.complete();
        }
        OperationRequest request = new OperationRequest();
        request.setProductNumber(parts[3]);
        request.setAmount(amount);
        log.info("Processing debit payment {} on account {}", paymentId, parts[3]);
        return transactionService.withdraw(request)
                .doOnSuccess(t -> publisher.completed(paymentId, t.getBalanceAfter()))
                .ignoreElement()
                .onErrorResumeNext(e -> {
                    log.warn("Debit payment {} failed: {}", paymentId, e.getMessage());
                    publisher.failed(paymentId, e.getMessage());
                    return Completable.complete();
                });
    }
}
