package com.bank.transaction.service.impl;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.dto.AccountResponse;
import com.bank.transaction.dto.CreditResponse;
import com.bank.transaction.dto.OperationRequest;
import com.bank.transaction.exception.BusinessException;
import com.bank.transaction.exception.NotFoundException;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link TransactionServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private CreditClient creditClient;

    @InjectMocks
    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fixedTermDay", 15);
    }

    // ---------- helpers ----------

    private OperationRequest request(String product, String amount) {
        OperationRequest r = new OperationRequest();
        r.setProductNumber(product);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    private AccountResponse account(String number, String type, Integer limit, String balance) {
        AccountResponse a = new AccountResponse();
        a.setAccountNumber(number);
        a.setAccountType(type);
        a.setMonthlyMovementLimit(limit);
        a.setBalance(new BigDecimal(balance));
        return a;
    }

    private CreditResponse credit(String type, String limit, String balance) {
        CreditResponse c = new CreditResponse();
        c.setCreditNumber("CR1");
        c.setCreditType(type);
        c.setCreditLimit(new BigDecimal(limit));
        c.setBalance(new BigDecimal(balance));
        return c;
    }

    private void saveReturnsSameEntity() {
        when(repository.save(any(Transaction.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
    }

    private void monthlyCountIs(String product, long count) {
        when(repository.countByProductNumberAndDateBetween(eq(product), any(), any()))
                .thenReturn(Mono.just(count));
    }

    // ---------- deposits and withdrawals ----------

    @Test
    void depositOnCheckingShouldRegisterTransactionWithoutCountingMovements() {
        when(accountClient.findByNumber("CHK1"))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "100")));
        when(accountClient.updateBalance("CHK1", new BigDecimal("50")))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "150")));
        saveReturnsSameEntity();

        service.deposit(request("CHK1", "50"))
                .test()
                .assertValue(t -> t.getTransactionType() == TransactionType.DEPOSIT
                        && t.getProductType() == ProductType.ACCOUNT
                        && new BigDecimal("150").compareTo(t.getBalanceAfter()) == 0)
                .assertComplete();

        verify(repository, never()).countByProductNumberAndDateBetween(any(), any(), any());
    }

    @Test
    void withdrawShouldSendNegativeAmountToAccountService() {
        when(accountClient.findByNumber("CHK1"))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "100")));
        when(accountClient.updateBalance("CHK1", new BigDecimal("-30")))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "70")));
        saveReturnsSameEntity();

        service.withdraw(request("CHK1", "30"))
                .test()
                .assertValue(t -> t.getTransactionType() == TransactionType.WITHDRAWAL)
                .assertComplete();

        verify(accountClient).updateBalance("CHK1", new BigDecimal("-30"));
    }

    @Test
    void withdrawShouldPropagateInsufficientFunds() {
        when(accountClient.findByNumber("CHK1"))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "10")));
        when(accountClient.updateBalance(eq("CHK1"), any()))
                .thenReturn(Mono.error(new BusinessException("Insufficient funds")));

        service.withdraw(request("CHK1", "999")).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void depositShouldFailWhenAccountLookupFails() {
        when(accountClient.findByNumber("X"))
                .thenReturn(Mono.error(new BusinessException("Account not found")));

        service.deposit(request("X", "10")).test().assertError(BusinessException.class);
        verify(accountClient, never()).updateBalance(any(), any());
    }

    // ---------- monthly limit ----------

    @Test
    void savingsShouldAllowMovementBelowMonthlyLimit() {
        when(accountClient.findByNumber("SAV1"))
                .thenReturn(Mono.just(account("SAV1", "SAVINGS", 10, "100")));
        monthlyCountIs("SAV1", 3);
        when(accountClient.updateBalance(eq("SAV1"), any()))
                .thenReturn(Mono.just(account("SAV1", "SAVINGS", 10, "150")));
        saveReturnsSameEntity();

        service.deposit(request("SAV1", "50")).test().assertComplete();
    }

    @Test
    void savingsShouldRejectMovementWhenMonthlyLimitReached() {
        when(accountClient.findByNumber("SAV1"))
                .thenReturn(Mono.just(account("SAV1", "SAVINGS", 10, "100")));
        monthlyCountIs("SAV1", 10);

        service.deposit(request("SAV1", "50")).test().assertError(BusinessException.class);
        verify(accountClient, never()).updateBalance(any(), any());
    }

    // ---------- fixed term ----------

    @Test
    void fixedTermShouldRejectMovementOnWrongDay() {
        int today = LocalDate.now().getDayOfMonth();
        ReflectionTestUtils.setField(service, "fixedTermDay", today == 1 ? 2 : 1);
        when(accountClient.findByNumber("FT1"))
                .thenReturn(Mono.just(account("FT1", "FIXED_TERM", 1, "100")));

        service.deposit(request("FT1", "50")).test().assertError(BusinessException.class);
        verify(repository, never()).countByProductNumberAndDateBetween(any(), any(), any());
    }

    @Test
    void fixedTermShouldAllowFirstMovementOnTheConfiguredDay() {
        ReflectionTestUtils.setField(service, "fixedTermDay", LocalDate.now().getDayOfMonth());
        when(accountClient.findByNumber("FT1"))
                .thenReturn(Mono.just(account("FT1", "FIXED_TERM", 1, "100")));
        monthlyCountIs("FT1", 0);
        when(accountClient.updateBalance(eq("FT1"), any()))
                .thenReturn(Mono.just(account("FT1", "FIXED_TERM", 1, "150")));
        saveReturnsSameEntity();

        service.deposit(request("FT1", "50")).test().assertComplete();
    }

    @Test
    void fixedTermShouldRejectSecondMovementOfTheMonth() {
        ReflectionTestUtils.setField(service, "fixedTermDay", LocalDate.now().getDayOfMonth());
        when(accountClient.findByNumber("FT1"))
                .thenReturn(Mono.just(account("FT1", "FIXED_TERM", 1, "100")));
        monthlyCountIs("FT1", 1);

        service.deposit(request("FT1", "50")).test().assertError(BusinessException.class);
    }

    // ---------- credit payments and charges ----------

    @Test
    void payShouldSendNegativeAmountToCreditService() {
        when(creditClient.findByNumber("CR1"))
                .thenReturn(Mono.just(credit("CREDIT_CARD", "1000", "300")));
        when(creditClient.updateBalance("CR1", new BigDecimal("-100")))
                .thenReturn(Mono.just(credit("CREDIT_CARD", "1000", "200")));
        saveReturnsSameEntity();

        service.pay(request("CR1", "100"))
                .test()
                .assertValue(t -> t.getTransactionType() == TransactionType.PAYMENT
                        && t.getProductType() == ProductType.CREDIT
                        && new BigDecimal("200").compareTo(t.getBalanceAfter()) == 0)
                .assertComplete();
    }

    @Test
    void chargeShouldWorkOnCreditCard() {
        when(creditClient.findByNumber("CR1"))
                .thenReturn(Mono.just(credit("CREDIT_CARD", "1000", "0")));
        when(creditClient.updateBalance("CR1", new BigDecimal("300")))
                .thenReturn(Mono.just(credit("CREDIT_CARD", "1000", "300")));
        saveReturnsSameEntity();

        service.charge(request("CR1", "300"))
                .test()
                .assertValue(t -> t.getTransactionType() == TransactionType.CHARGE)
                .assertComplete();
    }

    @Test
    void chargeShouldFailWhenCreditIsNotACard() {
        when(creditClient.findByNumber("CR1"))
                .thenReturn(Mono.just(credit("PERSONAL", "5000", "0")));

        service.charge(request("CR1", "100")).test().assertError(BusinessException.class);
        verify(creditClient, never()).updateBalance(any(), any());
    }

    // ---------- balances ----------

    @Test
    void getAccountBalanceShouldReturnBalanceAsAvailable() {
        when(accountClient.findByNumber("CHK1"))
                .thenReturn(Mono.just(account("CHK1", "CHECKING", null, "250")));

        service.getAccountBalance("CHK1")
                .test()
                .assertValue(b -> new BigDecimal("250").compareTo(b.getAvailable()) == 0
                        && b.getProductType() == ProductType.ACCOUNT)
                .assertComplete();
    }

    @Test
    void getCreditBalanceShouldCalculateAvailableCredit() {
        when(creditClient.findByNumber("CR1"))
                .thenReturn(Mono.just(credit("CREDIT_CARD", "1000", "200")));

        service.getCreditBalance("CR1")
                .test()
                .assertValue(b -> new BigDecimal("800").compareTo(b.getAvailable()) == 0
                        && new BigDecimal("200").compareTo(b.getBalance()) == 0)
                .assertComplete();
    }

    // ---------- history ----------

    private Transaction tx() {
        return Transaction.builder().id("t1").productNumber("CHK1")
                .productType(ProductType.ACCOUNT).transactionType(TransactionType.DEPOSIT)
                .amount(BigDecimal.TEN).balanceAfter(BigDecimal.TEN)
                .date(LocalDateTime.now()).build();
    }

    @Test
    void findAllShouldReturnAllTransactions() {
        when(repository.findAll()).thenReturn(Flux.just(tx(), tx()));
        service.findAll().test().assertValueCount(2).assertComplete();
    }

    @Test
    void findByIdShouldReturnTransaction() {
        when(repository.findById("t1")).thenReturn(Mono.just(tx()));
        service.findById("t1").test().assertValue(t -> "t1".equals(t.getId())).assertComplete();
    }

    @Test
    void findByIdShouldFailWhenMissing() {
        when(repository.findById("x")).thenReturn(Mono.empty());
        service.findById("x").test().assertError(NotFoundException.class);
    }

    @Test
    void findByProductShouldReturnMovementsOfTheProduct() {
        when(repository.findByProductNumberOrderByDateDesc("CHK1")).thenReturn(Flux.just(tx()));
        service.findByProduct("CHK1").test().assertValueCount(1).assertComplete();
    }
}