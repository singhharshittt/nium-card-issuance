package com.nium.virtualcard.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Qualifier("globalBandwidth")
    private final Bandwidth globalBandwidth;

    @Qualifier("cardCreationBandwidth")
    private final Bandwidth cardCreationBandwidth;

    @Qualifier("financialOpsBandwidth")
    private final Bandwidth financialOpsBandwidth;

    @Qualifier("readOpsBandwidth")
    private final Bandwidth readOpsBandwidth;

    private final MeterRegistry meterRegistry;

    private final Map<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> endpointBuckets = new ConcurrentHashMap<>();

    private Counter rateLimitExceededCounter;

    @Override
    protected void initFilterBean() throws ServletException {
        super.initFilterBean();
        this.rateLimitExceededCounter = Counter.builder("rate.limit.exceeded.count")
                .description("Number of requests that exceeded rate limits")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIP = getClientIP(request);
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        if (requestPath.startsWith("/api/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Bucket globalBucket = globalBuckets.computeIfAbsent(
                    clientIP + ":global",
                    key -> Bucket.builder().addLimit(globalBandwidth).build()
            );

            ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);

            if (!globalProbe.isConsumed()) {
                handleRateLimitExceeded(request, response, "Global rate limit exceeded", globalProbe);
                return;
            }

            Bandwidth endpointBandwidth = getEndpointBandwidth(requestPath, method);
            if (endpointBandwidth != null) {
                String endpointKey = clientIP + ":" + getEndpointKey(requestPath);

                Bucket endpointBucket = endpointBuckets.computeIfAbsent(
                        endpointKey,
                        key -> Bucket.builder().addLimit(endpointBandwidth).build()
                );

                ConsumptionProbe endpointProbe = endpointBucket.tryConsumeAndReturnRemaining(1);

                if (!endpointProbe.isConsumed()) {
                    handleRateLimitExceeded(request, response, "Endpoint rate limit exceeded", endpointProbe);
                    return;
                }
            }

            addRateLimitHeaders(response, globalProbe);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            filterChain.doFilter(request, response);
        }
    }

    private void handleRateLimitExceeded(HttpServletRequest request,
                                         HttpServletResponse response,
                                         String reason,
                                         ConsumptionProbe probe) throws IOException {
        rateLimitExceededCounter.increment();

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

        String jsonResponse = String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"%s\",\"retryAfterSeconds\":%d}",
                reason,
                retryAfterSeconds
        );

        response.getWriter().write(jsonResponse);

        log.warn("Rate limit exceeded for IP: {}, reason: {}", getClientIP(request), reason);
    }

    private void addRateLimitHeaders(HttpServletResponse response, ConsumptionProbe probe) {
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.setHeader("X-Rate-Limit-Reset",
                String.valueOf(System.currentTimeMillis() + TimeUnit.NANOSECONDS.toMillis(probe.getNanosToWaitForRefill())));
    }

    private Bandwidth getEndpointBandwidth(String requestPath, String method) {
        if (requestPath.equals("/api/cards") && "POST".equals(method)) {
            return cardCreationBandwidth;
        } else if (requestPath.matches("/api/cards/[^/]+/(top-ups|spends)") && "POST".equals(method)) {
            return financialOpsBandwidth;
        } else if ((requestPath.matches("/api/cards/[^/]+") && "GET".equals(method))
                || (requestPath.matches("/api/cards/[^/]+/transactions") && "GET".equals(method))) {
            return readOpsBandwidth;
        }
        return null;
    }

    private String getEndpointKey(String requestPath) {
        if (requestPath.equals("/api/cards")) {
            return "card-creation";
        } else if (requestPath.matches("/api/cards/[^/]+/(top-ups|spends)")) {
            return "financial-ops";
        } else if (requestPath.matches("/api/cards/[^/]+(/transactions)?")) {
            return "read-ops";
        }
        return "default";
    }

    private String getClientIP(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }
}