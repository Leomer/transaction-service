package com.bank.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    @Indexed
    private String productNumber;

    private ProductType productType;

    private TransactionType transactionType;

    private BigDecimal amount;

    /** Product balance right after the operation. */
    private BigDecimal balanceAfter;

    @Indexed
    private LocalDateTime date;
    /** Commission charged on this movement (zero when free). */
    private BigDecimal fee;
    /** Counterpart account in a transfer. */
    private String relatedProductNumber;
}
