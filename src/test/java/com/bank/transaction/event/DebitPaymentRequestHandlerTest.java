package com.bank.transaction.event;

import com.bank.transaction.dto.TransactionDto;
import com.bank.transaction.exception.BusinessException;
import com.bank.transaction.service.TransactionService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DebitPaymentRequestHandler}. */
@ExtendWith(MockitoExtension.class)
class DebitPaymentRequestHandlerTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private PaymentResultPublisher publisher;

    @InjectMocks
    private DebitPaymentRequestHandler handler;

    @Test
    void validRequestShouldWithdrawAndPublishCompleted() {
        TransactionDto tx = TransactionDto.builder().balanceAfter(new BigDecimal("74.50")).build();
        when(transactionService.withdraw(argThat(r -> "ACC1".equals(r.getProductNumber())
                && new BigDecimal("25.50").compareTo(r.getAmount()) == 0)))
                .thenReturn(Single.just(tx));

        handler.handle("REQUESTED|P1|DC1|ACC1|25.50").test().assertComplete();

        verify(publisher).completed("P1", new BigDecimal("74.50"));
        verify(publisher, never()).failed(any(), any());
    }

    @Test
    void rejectedWithdrawalShouldPublishFailedWithTheReason() {
        when(transactionService.withdraw(any()))
                .thenReturn(Single.error(new BusinessException("Insufficient funds")));

        handler.handle("REQUESTED|P1|DC1|ACC1|999").test().assertComplete();

        verify(publisher).failed("P1", "Insufficient funds");
        verify(publisher, never()).completed(any(), any());
    }

    @Test
    void invalidAmountShouldPublishFailed() {
        handler.handle("REQUESTED|P1|DC1|ACC1|abc").test().assertComplete();

        verify(publisher).failed(eq("P1"), any());
        verify(transactionService, never()).withdraw(any());
    }

    @Test
    void malformedMessageShouldBeIgnored() {
        handler.handle("garbage").test().assertComplete();

        verify(transactionService, never()).withdraw(any());
        verify(publisher, never()).failed(any(), any());
    }
}
