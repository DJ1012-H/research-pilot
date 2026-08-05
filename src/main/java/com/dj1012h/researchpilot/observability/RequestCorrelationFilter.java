package com.dj1012h.researchpilot.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Generates a server-owned correlation id for each synchronous HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String RESPONSE_HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        MDC.put(RequestCorrelation.REQUEST_ID_KEY, requestId);
        response.setHeader(RESPONSE_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            log.info("event=http_request_completed requestId={} method={} path={} status={} elapsedMs={}",
                    requestId, request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove(RequestCorrelation.TASK_ID_KEY);
            MDC.remove(RequestCorrelation.REQUEST_ID_KEY);
        }
    }
}
