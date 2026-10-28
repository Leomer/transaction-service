package com.bank.transaction.mapper;

import com.bank.transaction.dto.TransactionDto;
import com.bank.transaction.model.Transaction;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionDto toDto(Transaction e) {
        return TransactionDto.builder()
                .id(e.getId())
                .productNumber(e.getProductNumber())
                .productType(e.getProductType())
                .transactionType(e.getTransactionType())
                .amount(e.getAmount())
                .balanceAfter(e.getBalanceAfter())
                .date(e.getDate())
                .fee(e.getFee())
                .relatedProductNumber(e.getRelatedProductNumber())
                .build();
    }
}