package com.bank.transaction.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountResponse {
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private Integer monthlyMovementLimit;
}
