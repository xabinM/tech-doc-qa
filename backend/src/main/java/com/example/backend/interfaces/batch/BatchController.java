package com.example.backend.interfaces.batch;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.batch.dto.BatchJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 Job 수동 트리거 API.
 *
 * 운영 이슈 발생 시 또는 테스트 목적으로 스케줄을 기다리지 않고 즉시 Job을 실행할 수 있다.
 * 모든 엔드포인트는 인증된 사용자만 호출 가능하다 (SecurityConfig 참조).
 *
 * Job은 asyncJobLauncher 를 통해 비동기 실행되므로 HTTP 응답은 즉시 반환된다.
 * 실제 실행 결과는 Grafana 대시보드 또는 로그로 확인한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.api.enabled", havingValue = "true")
public class BatchController {

    private final JobLauncher asyncJobLauncher;

    @Qualifier("staleDataCleanupJob")
    private final Job staleDataCleanupJob;

    @Qualifier("dailyStatsJob")
    private final Job dailyStatsJob;

    @Qualifier("documentIndexingJob")
    private final Job documentIndexingJob;

    @PostMapping("/cleanup")
    public ApiResponse<BatchJobResponse> triggerCleanup(@AuthenticationPrincipal Long userId) {
        return triggerJob(staleDataCleanupJob, userId);
    }

    @PostMapping("/stats")
    public ApiResponse<BatchJobResponse> triggerStats(@AuthenticationPrincipal Long userId) {
        return triggerJob(dailyStatsJob, userId);
    }

    @PostMapping("/index")
    public ApiResponse<BatchJobResponse> triggerIndexing(@AuthenticationPrincipal Long userId) {
        return triggerJob(documentIndexingJob, userId);
    }

    private ApiResponse<BatchJobResponse> triggerJob(Job job, Long triggeredBy) {
        String jobName = job.getName();
        log.info("배치 수동 트리거 - job={}, triggeredBy=userId:{}", jobName, triggeredBy);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("triggeredAt", System.currentTimeMillis())
                    .addLong("triggeredBy", triggeredBy)
                    .toJobParameters();
            asyncJobLauncher.run(job, params);
            return ApiResponse.ok("배치 Job 실행이 요청되었습니다", BatchJobResponse.accepted(jobName));
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("배치 이미 실행 중 - job={}", jobName);
            return ApiResponse.ok("해당 Job이 이미 실행 중입니다", BatchJobResponse.alreadyRunning(jobName));
        } catch (JobInstanceAlreadyCompleteException e) {
            return ApiResponse.ok("배치 Job 실행이 요청되었습니다", BatchJobResponse.accepted(jobName));
        } catch (JobRestartException | JobParametersInvalidException e) {
            log.error("배치 실행 요청 실패 - job={}, error={}", jobName, e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
