package com.example.backend.infrastructure.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배치 Job 자동 실행 스케줄러.
 *
 * 크론 표현식은 application.yaml 의 batch.schedule.* 값으로 제어한다.
 * 운영 환경에서 스케줄을 변경하려면 환경변수로 오버라이드하면 된다:
 *   BATCH_SCHEDULE_CLEANUP_CRON="0 30 1 * * *"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher asyncJobLauncher;

    @Qualifier("staleDataCleanupJob")
    private final Job staleDataCleanupJob;

    @Qualifier("dailyStatsJob")
    private final Job dailyStatsJob;

    @Qualifier("documentIndexingJob")
    private final Job documentIndexingJob;

    @Scheduled(cron = "${batch.schedule.cleanup-cron:0 0 2 * * *}")
    public void scheduleCleanup() {
        launch(staleDataCleanupJob);
    }

    @Scheduled(cron = "${batch.schedule.stats-cron:0 0 1 * * *}")
    public void scheduleStats() {
        launch(dailyStatsJob);
    }

    @Scheduled(cron = "${batch.schedule.indexing-cron:0 0 3 * * 0}")
    public void scheduleIndexing() {
        launch(documentIndexingJob);
    }

    private void launch(Job job) {
        String jobName = job.getName();
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("launchedAt", System.currentTimeMillis())
                    .toJobParameters();
            asyncJobLauncher.run(job, params);
            log.info("배치 스케줄 실행 - job={}", jobName);
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("배치 이미 실행 중 - job={}", jobName);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("배치 이미 완료됨 - job={}", jobName);
        } catch (JobRestartException | JobParametersInvalidException e) {
            log.error("배치 실행 실패 - job={}, error={}", jobName, e.getMessage(), e);
        }
    }
}
