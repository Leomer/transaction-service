package com.bank.transaction.client;

import com.bank.transaction.dto.CreditResponse;
import com.bank.transaction.dto.CreditResponse;
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

/** Calls credit-service. */
@Slf4j
@Component
public class CreditClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public CreditClient(WebClient.Builder builder,
                         @Value("${services.credit.url}") String baseUrl,
                         ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = builder.clone().baseUrl(baseUrl).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public Mono<CreditResponse> findByNumber(String creditNumber) {
        Mono<CreditResponse> call = webClient.get()
                .uri("/credits/{n}", creditNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(CreditResponse.class);
        return circuitBreakerFactory.create("credit-service").run(call, this::fallback);
    }

    public Mono<CreditResponse> updateBalance(String creditNumber, BigDecimal amount) {
        Mono<CreditResponse> call = webClient.patch()
                .uri("/credits/{n}/balance", creditNumber)
                .bodyValue(new BalanceUpdate(amount))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toBusinessException)
                .bodyToMono(CreditResponse.class);
        return circuitBreakerFactory.create("credit-service").run(call, this::fallback);
    }

    private Mono<CreditResponse> fallback(Throwable ex) {
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        log.warn("credit-service fallback triggered: {}", ex.toString());
        return Mono.error(new ServiceUnavailableException("credit-service is unavailable, try again later"));
    }

    /** Converts an error response from credit-service into a BusinessException. */
    private Mono<BusinessException> toBusinessException(ClientResponse response) {
        return response.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() { })
                .map(body -> new BusinessException(body.getOrDefault("error", "Request rejected")))
                .defaultIfEmpty(new BusinessException("Request rejected"));
    }
}