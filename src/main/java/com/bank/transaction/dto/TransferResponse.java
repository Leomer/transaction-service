package com.bank.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a transfer: scope (OWN / THIRD_PARTY) and both movements. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private String scope;
    private TransactionDto debit;
    private TransactionDto credit;
}