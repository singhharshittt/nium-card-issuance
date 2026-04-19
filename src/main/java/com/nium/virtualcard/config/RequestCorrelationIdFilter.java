package com.nium.virtualcard.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter for managing request correlation IDs in MDC (Mapped Diagnostic Context).
 *
 * This filter ensures that every request has a unique correlation ID that is:
 * - Generated if not provided by the client (X-Correlation-ID header)
 * - Propagated throughout the entire request lifecycle
 * - Stored in SLF4J MDC for inclusion in all logs
 *
 * This enables tracing a single request across all application logs and makes it easier
 * to correlate logs for debugging and monitoring purposes.
 */
@Component
public class RequestCorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            // Extract or generate correlation ID
            String correlationId = extractCorrelationId(servletRequest);

            // Put in MDC so it's available in all logs for this request
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Also set it in response header for client to use in subsequent requests
            if (servletResponse instanceof HttpServletResponse) {
                ((HttpServletResponse) servletResponse).setHeader(CORRELATION_ID_HEADER, correlationId);
            }

            // Continue with the request
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            // Clean up MDC to prevent memory leaks (important in thread pools)
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Extract correlation ID from request header, or generate a new one if not provided.
     *
     * @param servletRequest the servlet request
     * @return the correlation ID (either from header or newly generated)
     */
    private String extractCorrelationId(ServletRequest servletRequest) {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            String providedId = httpRequest.getHeader(CORRELATION_ID_HEADER);

            // Use provided ID if valid (non-empty), otherwise generate new one
            if (providedId != null && !providedId.trim().isEmpty()) {
                return providedId;
            }
        }

        // Generate new correlation ID as UUID
        return UUID.randomUUID().toString();
    }
}


