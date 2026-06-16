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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    // 다운스트림(rag-server) 전파 및 클라이언트 추적용 — 다른 컴포넌트에서 재사용
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final String USER_ID = "userId";
    private static final int REQUEST_ID_MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        // 클라이언트·다운스트림이 동일 요청을 추적할 수 있도록 응답 헤더에 반영
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startMs = System.currentTimeMillis();

        try {
            // [VT] = 가상 스레드, [PT] = 플랫폼 스레드 — 가상 스레드 전환 확인용
            log.debug("→ {} {} [{}]", request.getMethod(), request.getRequestURI(),
                    Thread.currentThread().isVirtual() ? "VT" : "PT");
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            // 수집기가 필드 단위로 파싱·집계할 수 있도록 구조화(JSON 필드)로 남긴다
            log.info("access",
                    kv("http_method", request.getMethod()),
                    kv("uri", request.getRequestURI()),
                    kv("status", response.getStatus()),
                    kv("duration_ms", durationMs),
                    // forward-headers-strategy=native 로 RemoteIpValve가 신뢰 프록시 기준 보정한 IP
                    kv("client_ip", request.getRemoteAddr()));
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    /**
     * 인바운드 X-Request-Id 가 있으면 재사용(게이트웨이/상위 서비스에서 시작된 추적 ID 연계),
     * 없거나 유효하지 않으면 새로 생성한다.
     * 클라이언트가 제어하는 값이므로 안전 문자만 남기고 길이를 제한해 로그 위조를 방지한다.
     */
    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(inbound)) {
            String sanitized = inbound.replaceAll("[^A-Za-z0-9-]", "");
            if (!sanitized.isEmpty()) {
                return sanitized.length() > REQUEST_ID_MAX_LENGTH
                        ? sanitized.substring(0, REQUEST_ID_MAX_LENGTH)
                        : sanitized;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // actuator 엔드포인트는 로깅 제외
        return request.getRequestURI().startsWith("/actuator");
    }
}
