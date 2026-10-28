package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceDto {
    private String productNumber;
    private ProductType productType;
    private BigDecimal balance;
    private BigDecimal available;
}
