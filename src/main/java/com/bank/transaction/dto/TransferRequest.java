package com.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Request body for a transfer between two bank accounts. */
@Data
public class TransferRequest {

    @NotBlank(message = "sourceAccount is required")
    private String sourceAccount;

    @NotBlank(message = "targetAccount is required")
    private String targetAccount;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;
}