package com.example.backend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * @Async 실행기를 가상 스레드 per-task executor로 교체한다.
     * spring.threads.virtual.enabled=true 가 Tomcat·기본 executor를 전환하지만,
     * 명시적 설정으로 스레드 이름을 부여해 로그 추적을 용이하게 한다.
     */
    @Override
    public Executor getAsyncExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("async-vt-", 0).factory()
        );
    }

    /**
     * @Async 메서드에서 잡히지 않은 예외를 중앙에서 처리한다.
     * (async-event 규칙: AsyncUncaughtExceptionHandler 등록 필수)
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("@Async 처리 중 예외 발생 - method={}, error={}",
                        method.getName(), throwable.getMessage(), throwable);
    }
}
