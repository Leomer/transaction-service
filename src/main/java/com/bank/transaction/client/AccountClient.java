package com.bank.transaction.client;

import com.bank.transaction.dto.AccountResponse;
import com.bank.transaction.dto.BalanceUpdate;
import com.bank.transaction.exception.BusinessException;
import com.bank.transaction.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
public class AccountClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public AccountClient(WebClient.Builder builder,
                         @Value("${services.account.url}") String baseUrl,
                         ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = builder.clone().baseUrl(baseUrl).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public Mono<AccountResponse> findByNumber(String accountNumber) {
        Mono<AccountResponse> call = webClient.get()
                .uri("/accounts/{n}", accountNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(AccountResponse.class);
        return circuitBreakerFactory.create("account-service").run(call, this::fallback);
    }

    public Mono<AccountResponse> updateBalance(String accountNumber, BigDecimal amount) {
        Mono<AccountResponse> call = webClient.patch()
                .uri("/accounts/{n}/balance", accountNumber)
                .bodyValue(new BalanceUpdate(amount))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(AccountResponse.class);
        return circuitBreakerFactory.create("account-service").run(call, this::fallback);
    }

    private Mono<AccountResponse> fallback(Throwable ex) {
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        log.warn("account-service fallback triggered: {}", ex.toString());
        return Mono.error(new ServiceUnavailableException("account-service is unavailable, try again later"));
    }

    /** Converts an error response from account-service into a BusinessException. */
    private Mono<BusinessException> toBusinessException(ClientResponse response) {
        return response.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() { })
                .map(body -> new BusinessException(body.getOrDefault("error", "Request rejected")))
                .defaultIfEmpty(new BusinessException("Request rejected"));
    }
}
