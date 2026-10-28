package com.bank.transaction.controller;

import com.bank.transaction.dto.*;
import com.bank.transaction.service.TransactionService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionDto> deposit(@Valid @RequestBody OperationRequest request) {
        return transactionService.deposit(request);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionDto> withdraw(@Valid @RequestBody OperationRequest request) {
        return transactionService.withdraw(request);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionDto> pay(@Valid @RequestBody OperationRequest request) {
        return transactionService.pay(request);
    }

    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionDto> charge(@Valid @RequestBody OperationRequest request) {
        return transactionService.charge(request);
    }

    @GetMapping("/balance/accounts/{accountNumber}")
    public Single<BalanceDto> accountBalance(@PathVariable String accountNumber) {
        return transactionService.getAccountBalance(accountNumber);
    }

    @GetMapping("/balance/credits/{creditNumber}")
    public Single<BalanceDto> creditBalance(@PathVariable String creditNumber) {
        return transactionService.getCreditBalance(creditNumber);
    }

    @GetMapping
    public Flowable<TransactionDto> findAll() {
        return transactionService.findAll();
    }

    @GetMapping("/{id}")
    public Single<TransactionDto> findById(@PathVariable String id) {
        return transactionService.findById(id);
    }

    @GetMapping("/product/{productNumber}")
    public Flowable<TransactionDto> findByProduct(@PathVariable String productNumber) {
        return transactionService.findByProduct(productNumber);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(request);
    }

    @GetMapping("/product/{productNumber}/between")
    public Flowable<TransactionDto> findBetween(
            @PathVariable String productNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transactionService.findByProductBetween(productNumber, from, to);
    }

    @GetMapping("/product/{productNumber}/last")
    public Flowable<TransactionDto> findLast(@PathVariable String productNumber) {
        return transactionService.findLastByProduct(productNumber);
    }
}