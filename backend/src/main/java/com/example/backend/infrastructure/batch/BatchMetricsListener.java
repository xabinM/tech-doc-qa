package com.example.backend.infrastructure.batch;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 모든 배치 Job에 공통으로 적용되는 메트릭 리스너.
 *
 * Prometheus에 노출되는 메트릭:
 *   batch_job_executions_total{job, status}   — Job 실행 횟수
 *   batch_job_duration_seconds{job, status}   — Job 실행 시간
 *   batch_items_processed_total{job}          — 처리된 아이템 합계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchMetricsListener implements JobExecutionListener {

    private final MeterRegistry meterRegistry;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("배치 시작 - job={}, params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();

        Duration duration = Duration.between(
                jobExecution.getStartTime(),
                jobExecution.getEndTime() != null ? jobExecution.getEndTime() : jobExecution.getStartTime()
        );

        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        long skipCount = jobExecution.getStepExecutions().stream()
                .mapToLong(se -> se.getReadSkipCount() + se.getWriteSkipCount())
                .sum();

        log.info("배치 완료 - job={}, status={}, writeCount={}, skipCount={}, duration={}ms",
                jobName, status, writeCount, skipCount, duration.toMillis());

        // job/status 조합이 런타임에 결정되므로 @PostConstruct 사전 등록 불가.
        // meterRegistry.timer/counter 는 동일 name+tag 조합에 대해 내부 캐시를 사용한다.
        meterRegistry.timer("batch.job.duration", "job", jobName, "status", status.name())
                .record(duration);

        meterRegistry.counter("batch.job.executions", "job", jobName, "status", status.name())
                .increment();

        if (writeCount > 0) {
            meterRegistry.counter("batch.items.processed", "job", jobName)
                    .increment(writeCount);
        }
    }
}
