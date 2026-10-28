package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private String id;
    private String productNumber;
    private ProductType productType;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDateTime date;
    /** Commission charged on this movement (zero when free). */
    private BigDecimal fee;
}
