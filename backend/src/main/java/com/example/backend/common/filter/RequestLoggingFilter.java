package com.example.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put(REQUEST_ID, requestId);
        long startMs = System.currentTimeMillis();

        try {
            // [VT] = 가상 스레드, [PT] = 플랫폼 스레드 — 가상 스레드 전환 확인용
            log.debug("→ {} {} [{}]", request.getMethod(), request.getRequestURI(),
                    Thread.currentThread().isVirtual() ? "VT" : "PT");
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("← {} {} {} {}ms", response.getStatus(), request.getMethod(), request.getRequestURI(), durationMs);
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // actuator 엔드포인트는 로깅 제외
        return request.getRequestURI().startsWith("/actuator");
    }
}
