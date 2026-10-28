package com.bank.transaction.service.impl;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.dto.AccountResponse;
import com.bank.transaction.dto.BalanceDto;
import com.bank.transaction.dto.OperationRequest;
import com.bank.transaction.dto.TransactionDto;
import com.bank.transaction.exception.BusinessException;
import com.bank.transaction.exception.NotFoundException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountClient accountClient;
    private final CreditClient creditClient;

    @Value("${business.fixed-term-day}")
    private int fixedTermDay;

    @Override
    public Single<TransactionDto> deposit(OperationRequest request) {
        log.info("Deposit of {} into account {}", request.getAmount(), request.getProductNumber());
        return accountOperation(request, TransactionType.DEPOSIT, request.getAmount());
    }

    @Override
    public Single<TransactionDto> withdraw(OperationRequest request) {
        log.info("Withdrawal of {} from account {}", request.getAmount(), request.getProductNumber());
        return accountOperation(request, TransactionType.WITHDRAWAL, request.getAmount().negate());
    }

    @Override
    public Single<TransactionDto> pay(OperationRequest request) {
        log.info("Payment of {} to credit {}", request.getAmount(), request.getProductNumber());
        return Single.fromPublisher(creditClient.findByNumber(request.getProductNumber()))
                .flatMap(credit -> Single.fromPublisher(
                        creditClient.updateBalance(credit.getCreditNumber(), request.getAmount().negate())))
                .flatMap(updated -> save(request, ProductType.CREDIT, TransactionType.PAYMENT,
                        updated.getBalance()));
    }

    @Override
    public Single<TransactionDto> charge(OperationRequest request) {
        log.info("Charge of {} to credit card {}", request.getAmount(), request.getProductNumber());
        return Single.fromPublisher(creditClient.findByNumber(request.getProductNumber()))
                .flatMap(credit -> "CREDIT_CARD".equals(credit.getCreditType())
                        ? Single.just(credit)
                        : Single.<com.bank.transaction.dto.CreditResponse>error(
                        new BusinessException("Charges are only allowed on credit cards")))
                .flatMap(credit -> Single.fromPublisher(
                        creditClient.updateBalance(credit.getCreditNumber(), request.getAmount())))
                .flatMap(updated -> save(request, ProductType.CREDIT, TransactionType.CHARGE,
                        updated.getBalance()));
    }

    @Override
    public Single<BalanceDto> getAccountBalance(String accountNumber) {
        return Single.fromPublisher(accountClient.findByNumber(accountNumber))
                .map(a -> BalanceDto.builder()
                        .productNumber(a.getAccountNumber())
                        .productType(ProductType.ACCOUNT)
                        .balance(a.getBalance())
                        .available(a.getBalance())
                        .build());
    }

    @Override
    public Single<BalanceDto> getCreditBalance(String creditNumber) {
        return Single.fromPublisher(creditClient.findByNumber(creditNumber))
                .map(c -> BalanceDto.builder()
                        .productNumber(c.getCreditNumber())
                        .productType(ProductType.CREDIT)
                        .balance(c.getBalance())
                        .available(c.getCreditLimit().subtract(c.getBalance()))
                        .build());
    }

    @Override
    public Flowable<TransactionDto> findAll() {
        return Flowable.fromPublisher(repository.findAll()).map(TransactionMapper::toDto);
    }

    @Override
    public Single<TransactionDto> findById(String id) {
        return Maybe.fromPublisher(repository.findById(id))
                .switchIfEmpty(Single.error(new NotFoundException("Transaction not found: " + id)))
                .map(TransactionMapper::toDto);
    }

    @Override
    public Flowable<TransactionDto> findByProduct(String productNumber) {
        return Flowable.fromPublisher(repository.findByProductNumberOrderByDateDesc(productNumber))
                .map(TransactionMapper::toDto);
    }

    /** Deposit/withdrawal flow: validate, charge fee if needed, update balance, register. */
    private Single<TransactionDto> accountOperation(OperationRequest request, TransactionType type,
                                                    BigDecimal signedAmount) {
        return Single.fromPublisher(accountClient.findByNumber(request.getProductNumber()))
                .flatMap(this::validateMovements)
                .flatMap(acc -> calculateFee(acc).flatMap(fee ->
                        // amount and fee are applied in ONE atomic balance update
                        Single.fromPublisher(accountClient.updateBalance(
                                        acc.getAccountNumber(), signedAmount.subtract(fee)))
                                .flatMap(updated -> save(request, ProductType.ACCOUNT, type,
                                        updated.getBalance().add(fee), fee))));
    }

    /** Fee is zero while the monthly free transactions are not exhausted. */
    private Single<BigDecimal> calculateFee(AccountResponse account) {
        Integer freeLimit = account.getFreeTransactionLimit();
        BigDecimal fee = account.getTransactionFee();
        log.info("calculateFee: freeLimit={}, fee={}", freeLimit, fee);
        if (freeLimit == null || fee == null) {
            return Single.just(BigDecimal.ZERO);
        }
        return countMonthlyMovements(account.getAccountNumber())
                .map(count -> count >= freeLimit ? fee : BigDecimal.ZERO);
    }

    /** Number of movements registered for the product in the current month. */
    private Single<Long> countMonthlyMovements(String productNumber) {
        YearMonth month = YearMonth.now();
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        return Single.fromPublisher(
                repository.countByProductNumberAndDateBetween(productNumber, from, to));
    }

    /** Applies the monthly movement limit and the fixed-term day rule. */
    private Single<AccountResponse> validateMovements(AccountResponse account) {
        Integer limit = account.getMonthlyMovementLimit();
        if (limit == null) {
            return Single.just(account); // checking accounts: unlimited
        }
        if ("FIXED_TERM".equals(account.getAccountType())
                && LocalDate.now().getDayOfMonth() != fixedTermDay) {
            return Single.error(new BusinessException(
                    "Fixed-term accounts only allow movements on day " + fixedTermDay + " of the month"));
        }
        return countMonthlyMovements(account.getAccountNumber())
                .flatMap(count -> count >= limit
                        ? Single.<AccountResponse>error(new BusinessException(
                        "Monthly movement limit reached (" + limit + ")"))
                        : Single.just(account));
    }

    private Single<TransactionDto> save(OperationRequest request, ProductType productType,
                                        TransactionType type, BigDecimal balanceAfter) {
        return save(request, productType, type, balanceAfter, BigDecimal.ZERO);
    }

    private Single<TransactionDto> save(OperationRequest request, ProductType productType,
                                        TransactionType type, BigDecimal balanceAfter,
                                        BigDecimal fee) {
        Transaction tx = Transaction.builder()
                .productNumber(request.getProductNumber())
                .productType(productType)
                .transactionType(type)
                .amount(request.getAmount())
                .fee(fee)
                .balanceAfter(balanceAfter)
                .date(LocalDateTime.now())
                .build();
        return Single.fromPublisher(repository.save(tx)).map(TransactionMapper::toDto);
    }
}