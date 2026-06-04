package com.example.backend.infrastructure.batch;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Batch 공통 인프라 설정.
 *
 * - AsyncJobLauncher: 가상 스레드 풀로 Job을 비동기 실행. REST 트리거 시 HTTP 응답을 즉시 반환하고
 *   Job은 백그라운드에서 처리된다.
 * - @EnableScheduling: BatchScheduler의 @Scheduled 크론 활성화.
 * - spring.batch.job.enabled=false 와 함께 사용해 시작 시 Job 자동 실행을 막는다.
 */
@Configuration
@EnableScheduling
public class BatchConfig {

    @Bean
    @Primary
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new VirtualThreadTaskExecutor("batch-job-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
