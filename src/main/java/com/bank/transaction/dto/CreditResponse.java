package com.bank.transaction.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditResponse {
    private String creditNumber;
    private String creditType;
    private BigDecimal creditLimit;
    private BigDecimal balance;
}
