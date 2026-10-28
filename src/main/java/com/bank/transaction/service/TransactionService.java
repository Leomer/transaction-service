package com.bank.transaction.service;

import com.bank.transaction.dto.BalanceDto;
import com.bank.transaction.dto.OperationRequest;
import com.bank.transaction.dto.TransactionDto;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

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
}