package com.bank.transaction.service;

import com.bank.transaction.dto.*;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.time.LocalDate;

public interface TransactionService {
    Single<TransactionDto> deposit(OperationRequest request);
    Single<TransactionDto> withdraw(OperationRequest request);
    Single<TransactionDto> pay(OperationRequest request);
    Single<TransactionDto> charge(OperationRequest request);
    Single<BalanceDto> getAccountBalance(String accountNumber);
    Single<BalanceDto> getCreditBalance(String creditNumber);
    Flowable<TransactionDto> findAll();
    Single<TransactionDto> findById(String id);
    Flowable<TransactionDto> findByProduct(String productNumber);
    Single<TransferResponse> transfer(TransferRequest request);
    Flowable<TransactionDto> findByProductBetween(String productNumber, LocalDate from, LocalDate to);
    Flowable<TransactionDto> findLastByProduct(String productNumber);
}