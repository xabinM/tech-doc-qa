package com.example.backend.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.backend.common.filter.RequestLoggingFilter.MDC_REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("제출 시점의 MDC(requestId)가 비동기 실행 스레드로 전파된다")
    void MDC가_비동기_스레드로_전파됨() throws Exception {
        Executor executor = asyncConfig.getAsyncExecutor();
        MDC.put(MDC_REQUEST_ID, "req-123");
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            captured.set(MDC.get(MDC_REQUEST_ID));
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("제출 시점에 MDC가 없으면 비동기 스레드에도 MDC가 없다")
    void 제출시점_MDC가_없으면_비동기_스레드에도_없음() throws Exception {
        Executor executor = asyncConfig.getAsyncExecutor();
        MDC.clear();
        AtomicReference<String> captured = new AtomicReference<>("UNSET");
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            captured.set(MDC.get(MDC_REQUEST_ID));
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get()).isNull();
    }
}
