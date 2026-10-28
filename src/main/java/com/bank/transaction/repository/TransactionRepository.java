package com.bank.transaction.repository;

import com.bank.transaction.model.Transaction;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

    Flux<Transaction> findByProductNumberOrderByDateDesc(String productNumber);
    Mono<Long> countByProductNumberAndDateBetween(String productNumber, LocalDateTime from, LocalDateTime to);
    Flux<Transaction> findByProductNumberAndDateBetweenOrderByDateDesc(String productNumber, LocalDateTime from, LocalDateTime to);
    Flux<Transaction> findTop10ByProductNumberOrderByDateDesc(String productNumber);
}
