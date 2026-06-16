package com.example.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.example.backend.common.filter.RequestLoggingFilter.MDC_REQUEST_ID;
import static com.example.backend.common.filter.RequestLoggingFilter.REQUEST_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("인바운드 RequestId가 없으면 새로 생성하고 응답 헤더에 반영한다")
    void 인바운드_RequestId_없으면_생성하고_응답헤더에_반영() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).isNotBlank();
        assertThat(response.getHeader(REQUEST_ID_HEADER)).isEqualTo(chain.requestIdDuringChain);
    }

    @Test
    @DisplayName("인바운드 X-Request-Id가 있으면 그대로 재사용한다")
    void 인바운드_RequestId_있으면_재사용() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        request.addHeader(REQUEST_ID_HEADER, "trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).isEqualTo("trace-abc-123");
        assertThat(response.getHeader(REQUEST_ID_HEADER)).isEqualTo("trace-abc-123");
    }

    @Test
    @DisplayName("인바운드 RequestId의 위험 문자를 제거해 로그 위조를 방지한다")
    void 인바운드_RequestId_위험문자_제거() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        request.addHeader(REQUEST_ID_HEADER, "abc\n123 INJECTED<script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).isEqualTo("abc123INJECTEDscript");
    }

    @Test
    @DisplayName("64자를 초과하는 인바운드 RequestId는 잘라낸다")
    void 인바운드_RequestId_64자_초과_절단() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        request.addHeader(REQUEST_ID_HEADER, "a".repeat(100));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).hasSize(64);
    }

    @Test
    @DisplayName("위험 문자만 들어와 비어버리면 RequestId를 새로 생성한다")
    void 인바운드_RequestId_정제후_빈값이면_생성() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        request.addHeader(REQUEST_ID_HEADER, "<<>> ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).isNotBlank();
    }

    @Test
    @DisplayName("필터 처리가 끝나면 MDC를 정리한다")
    void 필터_종료후_MDC_정리됨() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new CapturingChain());

        assertThat(MDC.get(MDC_REQUEST_ID)).isNull();
    }

    /** 체인 실행 중(=요청 처리 중) 시점의 MDC requestId 를 포착한다. */
    private static class CapturingChain implements FilterChain {
        String requestIdDuringChain;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.requestIdDuringChain = MDC.get(MDC_REQUEST_ID);
        }
    }
}
