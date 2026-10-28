package com.bank.transaction.service.impl;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.dto.*;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountClient accountClient;
    private final CreditClient creditClient;
    private final Clock clock;

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

    @Override
    public Single<TransferResponse> transfer(TransferRequest request) {
        log.info("Transfer of {} from {} to {}", request.getAmount(),
                request.getSourceAccount(), request.getTargetAccount());
        if (request.getSourceAccount().equals(request.getTargetAccount())) {
            return Single.error(new BusinessException("Source and target accounts must be different"));
        }
        return Single.fromPublisher(accountClient.findByNumber(request.getSourceAccount()))
                .flatMap(source -> Single.fromPublisher(accountClient.findByNumber(request.getTargetAccount()))
                        .flatMap(target -> validateMovements(source)
                                .flatMap(this::calculateFee)
                                .flatMap(fee -> executeTransfer(request, source, target, fee))));
    }

    @Override
    public Flowable<TransactionDto> findByProductBetween(String productNumber, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return Flowable.error(new BusinessException("Invalid date range"));
        }
        return Flowable.fromPublisher(repository.findByProductNumberAndDateBetweenOrderByDateDesc(
                        productNumber, from.atStartOfDay(), to.plusDays(1).atStartOfDay()))
                .map(TransactionMapper::toDto);
    }

    @Override
    public Flowable<TransactionDto> findLastByProduct(String productNumber) {
        return Flowable.fromPublisher(repository.findTop10ByProductNumberOrderByDateDesc(productNumber))
                .map(TransactionMapper::toDto);
    }

    /** Debits the source, credits the target and refunds the source if the credit fails. */
    private Single<TransferResponse> executeTransfer(TransferRequest request, AccountResponse source,
                                                     AccountResponse target, BigDecimal fee) {
        BigDecimal debit = request.getAmount().add(fee);
        String scope = Objects.equals(source.getCustomerDocument(), target.getCustomerDocument())
                ? "OWN" : "THIRD_PARTY";
        return Single.fromPublisher(accountClient.updateBalance(request.getSourceAccount(), debit.negate()))
                .flatMap(debited -> Single.fromPublisher(
                                accountClient.updateBalance(request.getTargetAccount(), request.getAmount()))
                        .onErrorResumeNext(ex -> refund(request.getSourceAccount(), debit, ex))
                        .flatMap(credited -> saveTransfer(request, scope, fee,
                                debited.getBalance(), credited.getBalance())));
    }

    /** Compensation: returns the debited amount to the source and keeps the original error. */
    private Single<AccountResponse> refund(String sourceAccount, BigDecimal amount, Throwable cause) {
        log.warn("Credit to target failed, refunding {} to {}: {}", amount, sourceAccount, cause.toString());
        return Single.fromPublisher(accountClient.updateBalance(sourceAccount, amount))
                .onErrorResumeNext(e -> {
                    log.error("CRITICAL: refund of {} to {} failed, manual review required", amount, sourceAccount, e);
                    return Single.error(cause);
                })
                .flatMap(refunded -> Single.<AccountResponse>error(cause));
    }

    private Single<TransferResponse> saveTransfer(TransferRequest request, String scope, BigDecimal fee,
                                                  BigDecimal sourceBalance, BigDecimal targetBalance) {
        LocalDateTime now = LocalDateTime.now(clock);
        Transaction out = Transaction.builder()
                .productNumber(request.getSourceAccount())
                .relatedProductNumber(request.getTargetAccount())
                .productType(ProductType.ACCOUNT).transactionType(TransactionType.TRANSFER_OUT)
                .amount(request.getAmount()).fee(fee).balanceAfter(sourceBalance).date(now).build();
        Transaction in = Transaction.builder()
                .productNumber(request.getTargetAccount())
                .relatedProductNumber(request.getSourceAccount())
                .productType(ProductType.ACCOUNT).transactionType(TransactionType.TRANSFER_IN)
                .amount(request.getAmount()).fee(BigDecimal.ZERO).balanceAfter(targetBalance).date(now).build();
        return Single.fromPublisher(repository.save(out))
                .flatMap(savedOut -> Single.fromPublisher(repository.save(in))
                        .map(savedIn -> TransferResponse.builder().scope(scope)
                                .debit(TransactionMapper.toDto(savedOut))
                                .credit(TransactionMapper.toDto(savedIn)).build()));
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
                                        updated.getBalance(), fee))));
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
        YearMonth month = YearMonth.now(clock);
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
                && LocalDate.now(clock).getDayOfMonth() != fixedTermDay) {
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
                .date(LocalDateTime.now(clock))
                .build();
        return Single.fromPublisher(repository.save(tx)).map(TransactionMapper::toDto);
    }
}