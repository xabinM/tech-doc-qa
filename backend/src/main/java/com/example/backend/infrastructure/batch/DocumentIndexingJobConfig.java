package com.example.backend.infrastructure.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 문서 색인 배치 Job.
 *
 * 설정된 문서 소스(URLs)를 RAG 서버의 /batch/index 로 전달해 Elasticsearch 색인을 갱신한다.
 *
 * 현재는 패턴 시연용 구현 — 실제 색인은 RAG 서버에 /batch/index 엔드포인트가 추가되면
 * indexDocumentSources() Tasklet 내 WebClient 호출로 교체한다.
 *
 * 확장 포인트:
 *   1. DB 테이블(document_sources)에서 소스 목록을 읽어 JdbcCursorItemReader 로 교체
 *   2. RAG 서버 응답을 기반으로 색인 성공/실패 이력을 DB에 저장
 *   3. 실패한 소스만 재시도하는 잡 파라미터 추가
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DocumentIndexingJobConfig {

    private final BatchMetricsListener metricsListener;

    @Value("${batch.indexing.doc-sources:https://docs.spring.io/spring-framework/reference/,https://docs.spring.io/spring-boot/reference/}")
    private List<String> docSources;

    @Bean
    public Job documentIndexingJob(JobRepository jobRepository, Step indexDocumentSourcesStep) {
        return new JobBuilder("documentIndexingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(metricsListener)
                .start(indexDocumentSourcesStep)
                .build();
    }

    @Bean
    public Step indexDocumentSourcesStep(JobRepository jobRepository,
                                         PlatformTransactionManager txManager) {
        return new StepBuilder("indexDocumentSourcesStep", jobRepository)
                .tasklet(indexDocumentSources(), txManager)
                .build();
    }

    private Tasklet indexDocumentSources() {
        return (contribution, chunkContext) -> {
            log.info("문서 색인 시작 - 대상 소스: {}건", docSources.size());

            for (String source : docSources) {
                log.info("색인 요청 → {}", source);
                // TODO: ragPort.batchIndex(source) 호출로 교체
                // RAG 서버 /batch/index 엔드포인트 구현 후 활성화
                contribution.incrementWriteCount(1);
            }

            log.info("문서 색인 완료 - {}건 처리", docSources.size());
            return RepeatStatus.FINISHED;
        };
    }
}
