package com.bank.transaction.client;

import com.bank.transaction.dto.CreditResponse;
import com.bank.transaction.dto.BalanceUpdate;
import com.bank.transaction.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

/** Calls account-service. */
@Slf4j
@Component
public class CreditClient {

    private final WebClient webClient;

    public CreditClient(@Value("${services.credit.url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /** Finds an account by number. */
    public Mono<CreditResponse> findByNumber(String accountNumber) {
        log.debug("Calling account-service for {}", accountNumber);
        return webClient.get()
                .uri("/credits/{n}", accountNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(CreditResponse.class);
    }

    /** Applies a signed amount to the account balance. */
    public Mono<CreditResponse> updateBalance(String accountNumber, BigDecimal amount) {
        return webClient.patch()
                .uri("/credits/{n}/balance", accountNumber)
                .bodyValue(new BalanceUpdate(amount))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(CreditResponse.class);
    }

    private Mono<BusinessException> toBusinessException(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() { })
                .map(body -> new BusinessException(body.getOrDefault("error", "Request rejected")))
                .defaultIfEmpty(new BusinessException("Request rejected"));
    }
}