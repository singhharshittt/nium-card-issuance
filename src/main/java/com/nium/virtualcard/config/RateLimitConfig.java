package com.nium.virtualcard.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Value("${rate-limiting.global.requests-per-minute:1000}")
    private int globalRequestsPerMinute;

    @Value("${rate-limiting.card-creation.requests-per-minute:10}")
    private int cardCreationRequestsPerMinute;

    @Value("${rate-limiting.financial-ops.requests-per-minute:50}")
    private int financialOpsRequestsPerMinute;

    @Value("${rate-limiting.read-ops.requests-per-minute:100}")
    private int readOpsRequestsPerMinute;

    @Bean
    public Bandwidth globalBandwidth() {
        return Bandwidth.classic(
                globalRequestsPerMinute,
                Refill.intervally(globalRequestsPerMinute, Duration.ofMinutes(1))
        );
    }

    @Bean
    public Bandwidth cardCreationBandwidth() {
        return Bandwidth.classic(
                cardCreationRequestsPerMinute,
                Refill.intervally(cardCreationRequestsPerMinute, Duration.ofMinutes(1))
        );
    }

    @Bean
    public Bandwidth financialOpsBandwidth() {
        return Bandwidth.classic(
                financialOpsRequestsPerMinute,
                Refill.intervally(financialOpsRequestsPerMinute, Duration.ofMinutes(1))
        );
    }

    @Bean
    public Bandwidth readOpsBandwidth() {
        return Bandwidth.classic(
                readOpsRequestsPerMinute,
                Refill.intervally(readOpsRequestsPerMinute, Duration.ofMinutes(1))
        );
    }
}